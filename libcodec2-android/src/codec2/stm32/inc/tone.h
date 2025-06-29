#ifndef _TONE_H
#define _TONE_H
/*!
 * Fixed-point tone generator.
 *
 * The code here implements a simple fixed-point tone generator that uses
 * integer arithmetic to generate a sinusoid at a fixed sample rate of
 * 16kHz.
 *
 * To set the initial state of the state machine, you specify a frequency
 * and duration using tone_reset.  The corresponding C file embeds a
 * sinusoid look-up table.  The total number of samples is computed for
 * the given time and used to initialise 'remain', 'time' is initialised
 * to 0, and 'step' gives the amount to increment 'time' by each iteration.
 *
 * The samples are retrieved by repeatedly calling tone_next.  This
 * advances 'time' and decrements 'remain'.  The tone is complete when
 * 'remain' is zero.
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

#include <stdint.h>

/*! Tone sampling rate in Hz. */
#define TONE_FS	16000

/*!
 * Tone generator state.  This holds the current state of the tone
 * generator in order to decide what sample to release next.
 */
struct tone_gen_t {
	/*! Current sample.  (Q12) */
	uint32_t	sample;
	/*!
	 * Time remaining in samples. (integer)  Playback is finished
	 * when this reaches zero.
	 */
	uint16_t	remain;
	/*!
	 * Subsample step (Q12).  This is the number of samples (or part
	 * thereof) to advance "sample".  Special case: when zero, sample
	 * is not advanced, silence is generated instead.
	 */
	uint16_t	step;
};

/*!
 * Re-set the tone generator.
 *
 * @param	tone_gen	Tone generator to reset.
 * @param	freq		Frequency in Hz, 0 = silence.
 * @param	duration	Duration in milliseconds.  0 to stop.
 */
void tone_reset(
	struct tone_gen_t* const tone_gen,
	uint16_t freq, uint16_t duration);

/*!
 * Retrieve the next sample from the tone generator.
 * @param	tone_gen	Tone generator to update.
 */
int16_t tone_next(
	struct tone_gen_t* const tone_gen);

/*!
 * Retrieve the current time in milliseconds.
 */
uint32_t tone_msec(const struct tone_gen_t* const tone_gen);

#endif
