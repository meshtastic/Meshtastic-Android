#ifndef _MORSE_H
#define _MORSE_H
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

#include "sfx.h"

/*!
 * Maximum length of a morse symbol, including gaps and termination.
 * Allowing for 8 actual sub-symbols (dahs and dits), that's up to
 * 8 gaps between plus a terminator.
 */
#define MORSE_SYM_LEN	(17)

/*!
 * Morse code playback state machine
 */
struct morse_player_t {
	/*! Symbol being transmitted */
	struct sfx_note_t	sym[MORSE_SYM_LEN];
	/*!
	 * Pointer to the string being emitted.  Playback is finished
	 * when this is NULL.
	 */
	const char*		msg;
	/*! Sound effect player state machine */
	struct sfx_player_t	sfx_player;
	/*! "Dit" period in milliseconds */
	uint16_t		dit_time;
	/*! Tone frequency */
	uint16_t		freq;
};

/*!
 * Play a morse code message.
 * @param	morse_player	Morse code player state machine
 * @param	msg		Message to play back (NULL == stop)
 */
void morse_play(struct morse_player_t* const morse_player,
		const char* msg);

/*!
 * Retrieve the next sample to be played.
 */
int16_t morse_next(struct morse_player_t* const morse_player);

#endif
