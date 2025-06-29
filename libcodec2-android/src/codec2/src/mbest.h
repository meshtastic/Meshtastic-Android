/*---------------------------------------------------------------------------*\

  FILE........: mbest.h
  AUTHOR......: David Rowe
  DATE CREATED: Jan 2017

  Multistage vector quantiser search algorithm that keeps multiple
  candidates from each stage.

\*---------------------------------------------------------------------------*/

/*
  Copyright David Rowe 2017

  All rights reserved.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License version 2.1, as
  published by the Free Software Foundation.  This program is
  distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, see <http://www.gnu.org/licenses/>.

*/

#ifndef __MBEST__
#define __MBEST__

#define MBEST_STAGES 4

struct MBEST_LIST {
    int   index[MBEST_STAGES];    /* index of each stage that lead us to this error */
    float error;
};

struct MBEST {
    int                entries;   /* number of entries in mbest list   */
    struct MBEST_LIST *list;
};

struct MBEST *mbest_create(int entries);
void mbest_destroy(struct MBEST *mbest);
void mbest_precompute_weight(float cb[], float w[], int k, int m);
void mbest_insert(struct MBEST *mbest, int index[], float error);
void mbest_search(const float  *cb, float vec[], int k, int m, struct MBEST *mbest, int index[]);
void mbest_search450(const float  *cb, float vec[], float w[], int k,int shorterK, int m, struct MBEST *mbest, int index[]);

void mbest_print(char title[], struct MBEST *mbest);

#endif
