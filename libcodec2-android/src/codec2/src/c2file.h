/*---------------------------------------------------------------------------*\

  FILE........: c2file.h
  AUTHOR......: Kevin Otte
  DATE CREATED: 2017-08-01

  Header structures for Codec2 file storage

\*---------------------------------------------------------------------------*/

const char c2_file_magic[3] = {0xc0, 0xde, 0xc2};

struct c2_header {
    char magic[3];
    char version_major;
    char version_minor;
    char mode;
    char flags;
};
