#ifndef _SOUNDS_H
#define _SOUNDS_H
/*!
 * Sound effect library.
 *
 * This provides some sound effects for the SM1000 UI.
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

/*! Start-up tune / selected tune */
extern const struct sfx_note_t sound_startup[];

/*! Returned tune */
extern const struct sfx_note_t sound_returned[];

/*! Click sound */
extern const struct sfx_note_t sound_click[];

/*! Death march tune */
extern const struct sfx_note_t sound_death_march[];

#endif
