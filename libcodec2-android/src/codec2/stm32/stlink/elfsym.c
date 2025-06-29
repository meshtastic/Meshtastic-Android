/*
  elfsym.c

  Read symbol adresses from a .elf file.

  Based on libelf-howto.c from: http://em386.blogspot.com

  Unit test with:

  gcc elfsym.c -o elfsym -D__UNITTEST__ -Wall -lelf
  ./elfsym elf_file.elf
*/

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <libelf.h>
#include <gelf.h>
#include "elfsym.h"

#define ERR -1

int elfsym_open(char file[]) {
    int fd; 		        /* File Descriptor             */
    char *base_ptr;		/* ptr to our object in memory */
    struct stat elf_stats;	/* fstat struct                */

    if((fd = open(file, O_RDWR)) == ERR) {
        printf("couldnt open %s\n", file);
        return ERR;
    }

    if((fstat(fd, &elf_stats))) {
        printf("could not fstat %s\n", file);
        close(fd);
        return ERR;
    }

    if((base_ptr = (char *) malloc(elf_stats.st_size)) == NULL) {
        fprintf(stderr, "could not malloc\n");
        close(fd);
        return ERR;
    }

    if((read(fd, base_ptr, elf_stats.st_size)) < elf_stats.st_size) {
        fprintf(stderr, "could not read %s\n", file);
        free(base_ptr);
        close(fd);
        return ERR;
    }

    /* Check libelf version first */

    if(elf_version(EV_CURRENT) == EV_NONE) {
        fprintf(stderr, "WARNING Elf Library is out of date!\n");
    }

    free(base_ptr);

    return fd;
}


void elfsym_close(int fd) {
    close(fd);
}

unsigned int elfsym_get_symbol_address(int fd, char symbol_name[])
{
    Elf_Scn     *scn;              /* Section Descriptor          */
    Elf_Data    *edata;            /* Data Descriptor             */
    GElf_Sym     sym;		   /* Symbol                      */
    GElf_Shdr    shdr;             /* Section Header              */
    Elf         *elf;              /* Our Elf pointer for libelf  */
    unsigned int symbol_address;
    int          symbol_count;
    int          i;

    /* Iterate through section headers, stop when we find symbols,
       and check for match */

    elf = elf_begin(fd, ELF_C_READ, NULL);
    if (elf == 0) {
        fprintf(stderr, "could not elf_begin\n");
    }
    symbol_address = 0;
    scn = NULL;

    while((scn = elf_nextscn(elf, scn)) != 0) {
        gelf_getshdr(scn, &shdr);

        // When we find a section header marked SHT_SYMTAB stop and get symbols
        edata = NULL;
        if(shdr.sh_type == SHT_SYMTAB) {
            // edata points to our symbol table
            edata = elf_getdata(scn, edata);

            // how many symbols are there? this number comes from the size of
            // the section divided by the entry size
            symbol_count = shdr.sh_size / shdr.sh_entsize;

            // loop through to grab all symbols
            for(i = 0; i < symbol_count; i++) {
                // libelf grabs the symbol data using gelf_getsym()
                gelf_getsym(edata, i, &sym);

                if (strcmp(symbol_name,
                           elf_strptr(elf, shdr.sh_link, sym.st_name)) == 0) {
                    symbol_address = sym.st_value;
                }
            }

        }
    }

    return symbol_address;
}

#ifdef __UNITTEST__

int main(int argc, char *argv[])
{
    int           fd;
    unsigned int  flag_addr, ptr_addr, file_addr, len_addr;

    fd = elfsym_open(argv[1]);
    flag_addr = elfsym_get_symbol_address(fd, "syscalls_gdb_flag");
    ptr_addr = elfsym_get_symbol_address(fd, "syscalls_gdb_ptr");
    file_addr = elfsym_get_symbol_address(fd, "syscalls_gdb_file");
    len_addr = elfsym_get_symbol_address(fd, "syscalls_gdb_len");
    elfsym_close(fd);

    printf("flag_addr: 0x%x\n", flag_addr);
    printf("ptr_addr: 0x%x\n", ptr_addr);
    printf("file_addr: 0x%x\n", file_addr);
    printf("len_addr: 0x%x\n", len_addr);

    return 0;
}

#endif
