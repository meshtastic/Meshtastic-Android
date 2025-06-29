#ifndef _SFX_H
#define _SFX_H
/*!
 * Sound effect player library.
 *
 * This implements a state machine for playing back various monophonic
 * sound effects such as morse code symbols, clicks and alert tones.
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

#include "tone.h"

/*!
 * A sound effect "note"
 */
struct sfx_note_t {
	/*! Note frequency.  0 == pause */
	uint16_t	freq;
	/*! Note duration in msec.   0 == end of effect */
	uint16_t	duration;
};

/*!
 * Sound effect player state machine
 */
struct sfx_player_t {
	/*!
	 * Pointer to the current "note".  When this is NULL,
	 * playback is complete.
	 */
	const struct sfx_note_t*	note;
	/*! Tone generator state machine */
	struct tone_gen_t		tone_gen;
};

/*!
 * Start playing a particular effect.
 * @param	sfx_player	Effect player state machine
 * @param	effect		Pointer to sound effect (NULL == stop)
 */
void sfx_play(struct sfx_player_t* const sfx_player,
		const struct sfx_note_t* effect);

/*!
 * Retrieve the next sample to be played.
 */
int16_t sfx_next(struct sfx_player_t* const sfx_player);

#endif
