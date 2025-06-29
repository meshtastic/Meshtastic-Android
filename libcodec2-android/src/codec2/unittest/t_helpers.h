/*---------------------------------------------------------------------------*\

  FILE........: t_helpers.c
  AUTHOR......: Phil Ayres
  DATE CREATED: July 2017

 * Simple helper functions for unit tests
 * 
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

#ifndef T_HELPERS_H
#define	T_HELPERS_H

void test(char * tfn);
void test_failed();
void test_failed_s(char * expected, char * res);
void test_failed_f(float expected, float res);

char *fn;


#endif	/* T_HELPERS_H */

