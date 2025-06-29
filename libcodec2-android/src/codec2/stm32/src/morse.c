/*!
 * Morse code library.
 *
 * This implements a state machine for playing back morse code messages.
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
#include "morse.h"

#include <stdio.h>

/*! Symbol table element definition */
struct morse_sym_table_t {
	uint8_t code; uint8_t len;
};

/*! Symbol table: "digits" */
static const struct morse_sym_table_t morse_digits[] = {
	{	.code = 0xf8,	.len = 5	},	/* 0: ----- */
	{	.code = 0x78,	.len = 5	},	/* 1: .---- */
	{	.code = 0x38,	.len = 5	},	/* 2: ..--- */
	{	.code = 0x18,	.len = 5	},	/* 3: ...-- */
	{	.code = 0x08,	.len = 5	},	/* 4: ....- */
	{	.code = 0x00,	.len = 5	},	/* 5: ..... */
	{	.code = 0x80,	.len = 5	},	/* 6: -.... */
	{	.code = 0xc0,	.len = 5	},	/* 7: --... */
	{	.code = 0xe0,	.len = 5	},	/* 8: ---.. */
	{	.code = 0xf0,	.len = 5	},	/* 9: ----. */
};

/*! Symbol table: "letters" */
static const struct morse_sym_table_t morse_letters[] = {
	{	.code = 0x40,	.len = 2	},	/* A: .-    */
	{	.code = 0x80,	.len = 4	},	/* B: -...  */
	{	.code = 0xa0,	.len = 4	},	/* C: -.-.  */
	{	.code = 0x80,	.len = 3	},	/* D: -..   */
	{	.code = 0x00,	.len = 1	},	/* E: .     */
	{	.code = 0x20,	.len = 4	},	/* F: ..-.  */
	{	.code = 0xc0,	.len = 3	},	/* G: --.   */
	{	.code = 0x00,	.len = 4	},	/* H: ....  */
	{	.code = 0x00,	.len = 2	},	/* I: ..    */
	{	.code = 0x70,	.len = 4	},	/* J: .---  */
	{	.code = 0xa0,	.len = 3	},	/* K: -.-   */
	{	.code = 0x40,	.len = 4	},	/* L: .-..  */
	{	.code = 0xc0,	.len = 2	},	/* M: --    */
	{	.code = 0x80,	.len = 2	},	/* N: -.    */
	{	.code = 0xe0,	.len = 3	},	/* O: ---   */
	{	.code = 0x60,	.len = 4	},	/* P: .--.  */
	{	.code = 0xd0,	.len = 4	},	/* Q: --.-  */
	{	.code = 0x40,	.len = 3	},	/* R: .-.   */
	{	.code = 0x00,	.len = 3	},	/* S: ...   */
	{	.code = 0x80,	.len = 1	},	/* T: -     */
	{	.code = 0x20,	.len = 3	},	/* U: ..-   */
	{	.code = 0x10,	.len = 4	},	/* V: ...-  */
	{	.code = 0x60,	.len = 3	},	/* W: .--   */
	{	.code = 0x90,	.len = 4	},	/* X: -..-  */
	{	.code = 0xb0,	.len = 4	},	/* Y: -.--  */
	{	.code = 0xc0,	.len = 4	},	/* Z: --..  */
};

static void morse_next_sym(struct morse_player_t* const morse_player)
{
	struct sfx_player_t* sfx_player = &(morse_player->sfx_player);

	if (!morse_player->msg) {
		sfx_play(sfx_player, NULL);
		return;
	}

	uint8_t sym_rem = 0;
	uint8_t sym_code = 0;
	const struct morse_sym_table_t* sym = NULL;
	const char* c = morse_player->msg;

	while(!sym) {
		if ((*c >= 'A') && (*c <= 'Z'))
			/* Play a letter. (capitals) */
			sym = &morse_letters[*c - 'A'];
		else if ((*c >= 'a') && (*c <= 'z'))
			/* Play a letter. (lowercase) */
			sym = &morse_letters[*c - 'a'];
		else if ((*c >= '0') && (*c <= '9'))
			/* Play a digit. */
			sym = &morse_digits[*c - '0'];
		else if (*c == 0) {
			morse_player->msg = NULL;
			return;
		}
		c++;
	}
	morse_player->msg = c;

	struct sfx_note_t* note = morse_player->sym;
	sym_rem = sym->len;
	sym_code = sym->code;

	while(sym_rem) {
		note->freq = morse_player->freq;
		if (sym_code & 0x80)
			/* Play a "dah" */
			note->duration = morse_player->dit_time*3;
		else
			/* Play a "dit" */
			note->duration = morse_player->dit_time;
		note++;
		sym_code <<= 1;
		sym_rem--;

		/* A gap follows */
		note->freq = 0;

		if (sym_rem) {
			/* More of the character */
			note->duration = morse_player->dit_time;
			note++;
		}
	}

	/* What comes next? */
	if (*c == ' ') {
		/* End of word */
		note->duration = morse_player->dit_time*7;
		note++;
	} else if (*c) {
		/* End of character */
		note->duration = morse_player->dit_time*3;
		note++;
	}

	/* Terminate the sequence */
	note->freq = 0;
	note->duration = 0;

	/* Set the player up */
	sfx_play(sfx_player, morse_player->sym);
}

/*!
 * Start playing a particular effect.
 * @param	sfx_player	Effect player state machine
 * @param	effect		Pointer to sound effect (NULL == stop)
 */
void morse_play(struct morse_player_t* const morse_player,
		const char* msg)
{
	morse_player->msg = msg;
	morse_next_sym(morse_player);
}

/*!
 * Retrieve the next sample to be played.
 */
int16_t morse_next(struct morse_player_t* const morse_player)
{
	if (!morse_player)
		return(0);
	if (!morse_player->sfx_player.note)
		morse_next_sym(morse_player);
	return sfx_next(&(morse_player->sfx_player));
}
