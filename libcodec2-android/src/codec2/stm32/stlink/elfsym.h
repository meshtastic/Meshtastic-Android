/*
  elfsym.h

  Read symbol adresses from a .elf file.
*/

#ifndef __ELFSYM__
#define __ELFSYM__

int elfsym_open(char file[]);
void elfsym_close(int fd);
unsigned int elfsym_get_symbol_address(int fd, char symbol_name[]);

#endif
