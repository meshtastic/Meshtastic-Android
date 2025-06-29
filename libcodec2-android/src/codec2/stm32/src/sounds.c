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

#include "sounds.h"

const struct sfx_note_t sound_startup[] = {
	{.freq = 600, .duration = 80},
	{.freq = 800, .duration = 80},
	{.freq = 1000, .duration = 80},
	{.freq = 0, .duration = 0}
};

const struct sfx_note_t sound_returned[] = {
	{.freq = 1000, .duration = 80},
	{.freq = 800, .duration = 80},
	{.freq = 600, .duration = 80},
	{.freq = 0, .duration = 0}
};

const struct sfx_note_t sound_click[] = {
	{.freq = 1200, .duration = 10},
	{.freq = 0, .duration = 0}
};

const struct sfx_note_t sound_death_march[] = {
	{.freq  = 340, 	.duration = 400},
	{.freq	= 0,	.duration = 80},
	{.freq  = 340, 	.duration = 400},
	{.freq	= 0,	.duration = 80},
	{.freq  = 340, 	.duration = 400},
	{.freq	= 0,	.duration = 80},
	{.freq  = 420, 	.duration = 400},
	{.freq	= 0,	.duration = 80},
	{.freq  = 400, 	.duration = 300},
	{.freq	= 0,	.duration = 80},
	{.freq  = 340, 	.duration = 120},
	{.freq	= 0,	.duration = 80},
	{.freq  = 340, 	.duration = 120},
	{.freq	= 0,	.duration = 80},
	{.freq  = 300, 	.duration = 200},
	{.freq	= 0,	.duration = 80},
	{.freq  = 340, 	.duration = 400},
	{.freq	= 0, 	.duration = 0},
};
