//==========================================================================
// Name:            reliable_text.h
//
// Purpose:         Handles reliable text (e.g. text with FEC).
// Created:         August 15, 2021
// Authors:         Mooneer Salem
//
// License:
//
//  This program is free software; you can redistribute it and/or modify
//  it under the terms of the GNU Lesser General Public License version 2.1,
//  as published by the Free Software Foundation.  This program is
//  distributed in the hope that it will be useful, but WITHOUT ANY
//  WARRANTY; without even the implied warranty of MERCHANTABILITY or
//  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
//  License for more details.
//
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program; if not, see <http://www.gnu.org/licenses/>.
//
//==========================================================================

#ifndef RELIABLE_TEXT_H
#define RELIABLE_TEXT_H

#ifdef __cplusplus
extern "C" {
#endif // __cplusplus

/* Forward define struct freedv for use by the function prototypes below. */
struct freedv;

/* Hide internals of reliable_text_t. */
typedef void* reliable_text_t;

/* Function type for callback (when full reliable text has been received). */
typedef void(*on_text_rx_t)(reliable_text_t rt, const char* txt_ptr, int length, void* state);

/* Allocate reliable_text object. */
reliable_text_t reliable_text_create();

/* Destroy reliable_text object. */
void reliable_text_destroy(reliable_text_t ptr);

/* Reset reliable_text object for next sync. */
void reliable_text_reset(reliable_text_t ptr);

/* Sets string that is sent on TX. */
void reliable_text_set_string(reliable_text_t ptr, const char* str, int strlength);

/* Link FreeDV object to reliable_text object. */
void reliable_text_use_with_freedv(reliable_text_t ptr, struct freedv* fdv, on_text_rx_t text_rx_fn, void* state);

/* Returns associated struct freedv object. */
struct freedv* reliable_text_get_freedv_obj(reliable_text_t ptr);

/* Unlink FreeDV object from reliable_text object. */
void reliable_text_unlink_from_freedv(reliable_text_t ptr);

#ifdef __cplusplus
}
#endif // __cplusplus
    
#endif // RELIABLE_TEXT_H
