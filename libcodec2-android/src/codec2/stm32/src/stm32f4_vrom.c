/*!
 * STM32F4 Virtual EEPROM driver
 *
 * This module implements a crude virtual EEPROM device stored in on-board
 * flash.  The STM32F405 has 4 16kB flash sectors starting at address
 * 0x80000000, followed by a 64kB sector, then 128kB sectors.
 *
 * The Cortex M4 core maps these all to address 0x00000000 when booting
 * from normal flash, so the first sector is reserved for interrupt
 * vectors.
 *
 * Everything else however is free game, and so we use these smaller
 * sectors to store our configuration.
 *
 * Author Stuart Longland <me@vk4msl.id.au>
 * Copyright (C) 2015 FreeDV project.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1,
 * as published by the Free Software Foundation.  This program is
 * distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, see
 * <http://www.gnu.org/licenses/>.
 */

#include <stdlib.h>
#include <string.h>
#include "stm32f4_vrom.h"
#include "stm32f4xx_flash.h"
#include "stm32f4xx_crc.h"

#define VROM_SECT_SZ		(16384)	/*!< Size of a flash sector */
#define VROM_SECT_CNT		(3)	/*!< Number of sectors */
#define VROM_BLOCK_SZ		(256)	/*!< Size of a flash block */

/*!
 * Starting address for the flash area
 */
#define VROM_START_ADDR		(0x08004000)

/*!
 * Number of blocks we can fit per sector, including the index block.
 */
#define VROM_BLOCK_CNT		(VROM_SECT_SZ / VROM_BLOCK_SZ)

/*!
 * Number of application blocks we can fit per sector.
 */
#define VROM_SECT_APP_BLOCK_CNT	(VROM_BLOCK_CNT - 1)

/*!
 * Total number of application blocks we can fit in flash.
 */
#define VROM_APP_BLOCK_CNT	(VROM_SECT_CNT * VROM_SECT_APP_BLOCK_CNT)

/*!
 * Maximum number of erase cycles per sector.
 * Table 42 (page 109) of STM32F405 datasheet (DocID022152 Rev 5).
 */
#define VROM_MAX_CYCLES		(10000)

/*!
 * EEPROM block header.
 */
struct __attribute__ ((__packed__)) vrom_block_hdr_t {
	/*!
	 * CRC32 checksum of the data, offset, size and ROM ID.
	 * A CRC32 of 0x00000000 indicates an obsoleted block.
	 * A CRC32 of 0xffffffff indicates an erased block.
	 */
	uint32_t		crc32;
	/*!
	 * ROM ID.
	 */
	uint8_t			rom;
	/*!
	 * Block number in the virtual EEPROM.
	 */
	uint8_t			idx;
	/*!
	 * Number of bytes from the virtual EEPROM stored in this block.
	 */
	uint8_t			size;
	/*!
	 * Reserved for future use.
	 */
	uint8_t			reserved;
};

/*!
 * The size of a block header in bytes.
 */
#define VROM_BLOCK_HDR_SZ	(sizeof(struct vrom_block_hdr_t))

/*!
 * The amount of data available for application use.
 */
#define VROM_DATA_SZ		(VROM_BLOCK_SZ - VROM_BLOCK_HDR_SZ)

/*!
 * EEPROM data block.
 */
struct __attribute__ ((__packed__)) vrom_data_block_t {
	/*! Block header */
	struct vrom_block_hdr_t		header;

	/*! Block data */
	uint8_t				data[VROM_DATA_SZ];
};

/*!
 * The first block in a sector is the sector index block.  This indicates
 * the used/free state of the entire block and counts the number of
 * erase cycles for the sector.  The index block has no header.
 */
struct __attribute__ ((__packed__)) vrom_sector_idx_t {
	/*!
	 * Number of erase cycles remaining for the sector.
	 * 0xffffffff == unprogrammed.
	 */
	uint32_t		cycles_remain;
	/*!
	 * Block metadata flags.  One for each data block in the sector.
	 * Does not include the index block.
	 */
	uint16_t		flags[VROM_SECT_APP_BLOCK_CNT];
};

#define VROM_SFLAGS_USED	(1 << 0)	/*!< Block in use */

/*!
 * Return the address of a virtual EEPROM sector header.
 */
static const struct vrom_sector_idx_t* vrom_get_sector_hdr(uint8_t sector)
{
	return (const struct vrom_sector_idx_t*)(
			VROM_START_ADDR + (VROM_SECT_SZ * sector));
}

/*!
 * Return the address of a virtual EEPROM block.
 */
static const struct vrom_data_block_t* vrom_get_block(
		uint8_t sector, uint8_t block)
{
	return (const struct vrom_data_block_t*)(
			(void*)vrom_get_sector_hdr(sector)
			+ (VROM_BLOCK_SZ * (block + 1)));
}

/*!
 * Compute the CRC32 of a block.
 */
static uint32_t vrom_crc32(
		const struct vrom_data_block_t* const block)
{
	struct vrom_data_block_t temp_block;
	uint32_t size = sizeof(temp_block);
	const uint8_t* in = (const uint8_t*)(&temp_block);
	uint32_t tmp;
	uint32_t crc;

	memcpy(&temp_block, block, sizeof(temp_block));
	temp_block.header.crc32 = 0;

	CRC_ResetDR();
	while(size) {
		tmp = 0;
		if (size) {
			tmp |= (uint32_t)(*(in++)) << 24;
			size--;
		}
		if (size) {
			tmp |= (uint32_t)(*(in++)) << 16;
			size--;
		}
		if (size) {
			tmp |= (uint32_t)(*(in++)) << 8;
			size--;
		}
		if (size) {
			tmp |= (uint32_t)(*(in++));
			size--;
		}
		crc = CRC_CalcCRC(tmp);
	}
	return crc;
}

/*!
 * Find the block storing the given index.
 */
static const struct vrom_data_block_t* vrom_find(uint8_t rom, uint8_t idx)
{
	int sector, block;

	for (sector = 0; sector < VROM_SECT_CNT; sector++) {
		const struct vrom_sector_idx_t* sect_hdr
			= vrom_get_sector_hdr(sector);
		if (sect_hdr->cycles_remain == UINT32_MAX)
			/* unformatted */
			continue;
		for (block = 0; block < VROM_SECT_APP_BLOCK_CNT; block++) {
			const struct vrom_data_block_t* block_ptr;
			if (sect_hdr->flags[block] == UINT16_MAX)
				/* unformatted */
				continue;
			if (sect_hdr->flags[block] == 0)
				/* obsolete */
				continue;

			block_ptr = vrom_get_block(sector, block);

			/* Verify the content */
			if (vrom_crc32(block_ptr)
					!= block_ptr->header.crc32)
				/* corrupt */
				continue;

			if (block_ptr->header.rom != rom)
				/* different ROM */
				continue;

			if (block_ptr->header.idx != idx)
				/* wrong index */
				continue;

			return block_ptr;
		}
	}
	return NULL;
}

/*!
 * Get the sector number of a given address.
 */
static uint8_t vrom_sector_num(const void* address)
{
	/* Get the offset from the base address */
	uint32_t offset = (uint32_t)address - VROM_START_ADDR;
	return offset / VROM_SECT_SZ;
}

/*!
 * Get the block number of a given address.
 */
static uint8_t vrom_block_num(const void* address)
{
	/* Get the sector number */
	uint8_t sector = vrom_sector_num(address);

	/* Get the offset from the sector base */
	uint32_t offset = (uint32_t)(address
			- (const void*)vrom_get_sector_hdr(sector));
	offset /= VROM_BLOCK_SZ;
	return offset - 1;
}

/*!
 * (Erase and) Format a sector.
 *
 * @retval	-EIO	Erase failed
 * @retval	-EPERM	Erase counter depleted.
 */
static int vrom_format_sector(const struct vrom_sector_idx_t* sector)
{
	uint8_t sector_num = vrom_sector_num(sector);
	uint32_t cycles_remain = VROM_MAX_CYCLES;
	if (sector->cycles_remain != UINT32_MAX) {
		if (sector->cycles_remain == 0)
			/* This sector is exhausted */
			return -EPERM;

		/* This sector has been formatted before */
		cycles_remain = sector->cycles_remain - 1;
		if (FLASH_EraseSector(sector_num + 1, VoltageRange_3))
			/* Erase failed */
			return -EIO;
	}

	/* Program the new sector cycle counter */
	if (FLASH_ProgramWord((uint32_t)sector,
				cycles_remain) == FLASH_COMPLETE)
		return 0;	/* All good */
	/* If we get here, then programming failed */
	return -EIO;
}

/*!
 * Find the next available block.
 */
static const struct vrom_data_block_t* vrom_find_free(uint8_t run_gc)
{
	int sector;
	if (run_gc) {
		for (sector = 0; sector < VROM_SECT_CNT; sector++) {
			uint8_t block;
			uint8_t used = 0;
			const struct vrom_sector_idx_t* sect_hdr
				= vrom_get_sector_hdr(sector);
			if (sect_hdr->cycles_remain == UINT32_MAX)
				/* Already erased */
				continue;
			if (sect_hdr->cycles_remain == 0)
				/* Depleted */
				continue;

			for (block = 0; block < VROM_SECT_APP_BLOCK_CNT;
					block++) {
				if (sect_hdr->flags[block]) {
					used = 1;
					break;
				}
			}

			if (!used) {
				/* We can format this */
				vrom_format_sector(sect_hdr);
			}
		}
	}

	for (sector = 0; sector < VROM_SECT_CNT; sector++) {
		uint8_t block;
		const struct vrom_sector_idx_t* sect_hdr
			= vrom_get_sector_hdr(sector);
		if (sect_hdr->cycles_remain == UINT32_MAX) {
			/* Unformatted sector. */
			if (vrom_format_sector(sect_hdr))
				/* Couldn't format, keep looking */
				continue;
		}
		for (block = 0; block < VROM_SECT_APP_BLOCK_CNT; block++) {
			if (sect_hdr->flags[block] == UINT16_MAX)
				/* Success */
				return vrom_get_block(sector, block);
		}
	}

	/* No blocks free, but have we done garbage collection? */
	if (!run_gc)
		return vrom_find_free(1);

	/* If we get here, then we weren't able to find a free block */
	return NULL;
}

/*!
 * Set flags for a block
 */
static int vrom_set_flags(const struct vrom_data_block_t* block,
		uint16_t flags)
{
	const struct vrom_sector_idx_t* sector =
		vrom_get_sector_hdr(vrom_sector_num(block));
	uint8_t block_num = vrom_block_num(block);

	/* Compute the new flags settings */
	flags = sector->flags[block_num] & ~flags;

	/* Write them */
	if (FLASH_ProgramHalfWord(
			(uint32_t)(&(sector->flags[block_num])),
			flags) != FLASH_COMPLETE)
		return -EIO;
	return 0;
}

/*!
 * Mark a block as being obsolete
 */
static int vrom_mark_obsolete(const struct vrom_data_block_t* block)
{
	/* Blank out the CRC */
	if (FLASH_ProgramWord((uint32_t)(&(block->header.crc32)), 0)
			!= FLASH_COMPLETE)
		return -EIO;
	/* Blank out the ROM ID */
	if (FLASH_ProgramByte((uint32_t)(&(block->header.rom)), 0)
			!= FLASH_COMPLETE)
		return -EIO;
	/* Blank out the index */
	if (FLASH_ProgramByte((uint32_t)(&(block->header.idx)), 0)
			!= FLASH_COMPLETE)
		return -EIO;
	/* Blank out the size */
	if (FLASH_ProgramByte((uint32_t)&(block->header.size), 0)
			!= FLASH_COMPLETE)
		return -EIO;
	/* Blank out the reserved byte */
	if (FLASH_ProgramByte((uint32_t)&(block->header.reserved), 0)
			!= FLASH_COMPLETE)
		return -EIO;
	/* Blank out the flags */
	return vrom_set_flags(block, -1);
}

/*!
 * Write a new block.
 */
static int vrom_write_block(uint8_t rom, uint8_t idx, uint8_t size,
		const uint8_t* in)
{
	/* Find a new home for the block */
	const struct vrom_data_block_t* block = vrom_find_free(0);
	struct vrom_data_block_t new_block;
	uint8_t* out = (uint8_t*)(block);
	uint32_t rem = sizeof(new_block);
	int res;

	if (!block)
		return -ENOSPC;

	/* Prepare the new block */
	memset(&new_block, 0xff, sizeof(new_block));
	new_block.header.rom = rom;
	new_block.header.idx = idx;
	new_block.header.size = size;
	memcpy(new_block.data, in, size);
	new_block.header.crc32 = vrom_crc32(&new_block);

	/* Start writing out the block */
	in = (uint8_t*)(&new_block);
	rem = VROM_BLOCK_SZ;
	while(rem) {
		if (*out != *in) {
			if (FLASH_ProgramByte((uint32_t)out, *in)
					!= FLASH_COMPLETE)
				/* Failed! */
				return -EIO;
		}
		in++;
		out++;
		rem--;
	}
	res = vrom_set_flags(block, VROM_SFLAGS_USED);
	if (res < 0)
		return res;
	return size;
}

/*!
 * Re-write the given block if needed.
 */
static int vrom_rewrite_block(const struct vrom_data_block_t* block,
		uint8_t size, const uint8_t* in)
{
	uint8_t obsolete = 0;
	uint8_t rom = block->header.rom;
	uint8_t idx = block->header.idx;
	const uint8_t* cmp_block = block->data;
	const uint8_t* cmp_in = in;
	uint8_t cmp_sz = size;
	int res;
	while(cmp_sz) {
		if (*cmp_block != *cmp_in) {
			obsolete = 1;
			break;
		}
		cmp_sz--;
		cmp_block++;
		cmp_in++;
	}

	if (!obsolete)
		/* The block is fine, leave it be. */
		return size;

	/* Mark the block as obsolete */
	res = vrom_mark_obsolete(block);
	if (res)
		return res;
	return vrom_write_block(rom, idx, size, in);
}

/*!
 * Overwrite the start of a block.
 */
static int vrom_overwrite_block(
		const struct vrom_data_block_t* block,
		uint8_t offset, uint8_t size, const uint8_t* in)
{
	uint8_t data[VROM_DATA_SZ];
	uint16_t block_sz = block->header.size;
	int res;

	if (!offset && (size >= block->header.size))
		/* Complete overwrite */
		return vrom_rewrite_block(block, size, in);

	if (offset) {
		/* Overwrite end of block, possible expansion */
		block_sz = offset + size;
		if (block_sz > VROM_DATA_SZ)
			block_sz = VROM_DATA_SZ;
		memcpy(data, block->data, offset);
		memcpy(&data[offset], in, block_sz - offset);
	} else {
		/* Overwrite start of block, no size change */
		memcpy(data, in, size);
		memcpy(&data[size], &(block->data[size]),
				block_sz - size);
	}

	res = vrom_rewrite_block(block, block_sz, data);
	if (res < 0)
		return res;
	return block_sz;
}

/*!
 * Write data to the virtual EEPROM.
 */
static int vrom_write_internal(uint8_t rom,
		uint16_t offset, uint16_t size, const uint8_t* in)
{
	/* Figure out our starting block and offset */
	uint8_t block_idx 	= offset / VROM_DATA_SZ;
	uint8_t block_offset	= offset % VROM_DATA_SZ;
	int count = 0;

	/* Locate the first block */
	const struct vrom_data_block_t* block = vrom_find(rom, block_idx);

	uint8_t block_sz = VROM_DATA_SZ;
	if (block_sz > (size + block_offset))
		block_sz = size + block_offset;

	if (!block) {
		/* Create a new block */
		uint8_t data[VROM_DATA_SZ];
		int res;
		memset(data, 0xff, sizeof(data));
		memcpy(&data[block_offset], in,
				block_sz-block_offset);
		res = vrom_write_block(rom, block_idx, block_sz, data);
		if (res < 0)
			return res;
	} else {
		/* Overwrite block */
		int res = vrom_overwrite_block(block, block_offset,
				block_sz, in);
		if (res < 0)
			return res;
		count += block_sz;
	}

	block_idx++;
	size -= block_sz - block_offset;

	while(size) {
		/* Work out how much data to write */
		if (size < VROM_DATA_SZ)
			block_sz = size;
		else
			block_sz = VROM_DATA_SZ;

		int res;

		/* Is there a block covering this range? */
		block = vrom_find(rom, block_idx);
		if (block)
			res = vrom_overwrite_block(
					block, 0, block_sz, in);
		else
			res = vrom_write_block(rom, block_idx,
					block_sz, in);

		if (res < 0)
			return res;

		/* Successful write */
		count += res;
		size -= res;
		in += res;
		offset += res;
	}
	return count;
}

/*!
 * Read data from a virtual EEPROM.
 * @param	rom		ROM ID to start reading.
 * @param	offset		Address offset into ROM to start reading.
 * @param	size		Number of bytes to read from ROM.
 * @param	out		Buffer to write ROM content to.
 * @returns			Number of bytes read from ROM.
 * @retval	-ENXIO		ROM not found
 * @retval	-ESPIPE		Offset past end of ROM.
 */
int vrom_read(uint8_t rom, uint16_t offset, uint16_t size, void* out)
{
	/* Figure out our starting block and offset */
	uint8_t block_idx 	= offset / VROM_DATA_SZ;
	uint8_t block_offset	= offset % VROM_DATA_SZ;
	uint8_t block_sz;
	int count = 0;
	uint8_t* out_ptr = (uint8_t*)out;

	/* Locate the first block */
	const struct vrom_data_block_t* block = vrom_find(rom, block_idx);

	if (!block)
		return -ENXIO;

	if (block_offset >= block->header.size)
		return -ESPIPE;

	/* Copy the initial bytes */
	block_sz = block->header.size - block_offset;
	if (block_sz > size)
		block_sz = size;
	memcpy(out_ptr, &(block->data[block_offset]), block_sz);
	out_ptr += block_sz;
	size -= block_sz;
	count += block_sz;

	if (size) {
		/* Look for the next block */
		block = vrom_find(rom, ++block_idx);
		while(size && block) {
			if (block->header.size <= size)
				block_sz = block->header.size;
			else
				block_sz = size;
			memcpy(out_ptr, block->data, block_sz);
			out_ptr += block_sz;
			size -= block_sz;
			count += block_sz;

			block = vrom_find(rom, ++block_idx);
		}
	}

	return count;
}

/*!
 * Write data to a virtual EEPROM.
 * @param	rom		ROM ID to start writing.
 * @param	offset		Address offset into ROM to start writing.
 * @param	size		Number of bytes to write to ROM.
 * @param	in		Buffer to write ROM content from.
 * @returns			Number of bytes written to ROM.
 * @retval	-EIO		Programming failed
 * @retval	-ENOSPC		No free blocks available
 */
int vrom_write(uint8_t rom, uint16_t offset, uint16_t size,
		const void* in)
{
	int res;
	FLASH_Unlock();
	FLASH_ClearFlag(FLASH_FLAG_EOP
			| FLASH_FLAG_OPERR
			| FLASH_FLAG_WRPERR
			| FLASH_FLAG_PGAERR
			| FLASH_FLAG_PGPERR
			| FLASH_FLAG_PGSERR);
	res = vrom_write_internal(rom, offset, size, in);
	FLASH_Lock();
	return res;
}

/*!
 * Erase a virtual EEPROM.
 * @param	rom		ROM ID to erase.
 * @returns			Number of bytes written to ROM.
 * @retval	-EIO		Programming failed
 * @retval	-ENOSPC		No free blocks available
 */
int vrom_erase(uint8_t rom)
{
	int sector, block;
	FLASH_Unlock();
	FLASH_ClearFlag(FLASH_FLAG_EOP
			| FLASH_FLAG_OPERR
			| FLASH_FLAG_WRPERR
			| FLASH_FLAG_PGAERR
			| FLASH_FLAG_PGPERR
			| FLASH_FLAG_PGSERR);
	for (sector = 0; sector < VROM_SECT_CNT; sector++) {
		const struct vrom_sector_idx_t* sect_hdr
			= vrom_get_sector_hdr(sector);
		if (sect_hdr->cycles_remain == UINT32_MAX)
			/* unformatted */
			continue;
		for (block = 0; block < VROM_SECT_APP_BLOCK_CNT; block++) {
			int res;
			const struct vrom_data_block_t* block_ptr;
			if (sect_hdr->flags[block] == UINT16_MAX)
				/* unformatted */
				continue;
			if (sect_hdr->flags[block] == 0)
				/* obsolete */
				continue;

			block_ptr = vrom_get_block(sector, block);

			/* Verify the content */
			if (vrom_crc32(block_ptr)
					!= block_ptr->header.crc32)
				/* corrupt */
				continue;

			if (block_ptr->header.rom != rom)
				/* different ROM */
				continue;

			/*
			 * Block is valid, for the correct ROM.  Mark it
			 * obsolete.
			 */
			res = vrom_mark_obsolete(block_ptr);
			if (res)
				return res;
		}
	}
	return 0;
}
