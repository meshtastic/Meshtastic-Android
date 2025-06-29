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

#include "tone.h"

/*! Fixed-point shift factor */
#define TONE_SHIFT	(12)

/*! Static compiled quarter-sinusoid. */
static const int16_t partial_sine[] = {
   830,   2488,   4140,   5781,   7407,   9014,  10598,  12155,
 13681,  15171,  16623,  18031,  19394,  20707,  21967,  23170,
 24314,  25395,  26411,  27360,  28238,  29043,  29774,  30429,
 31006,  31503,  31919,  32253,  32504,  32672,  32756
};

/*! Length of quarter-sinusoid in samples */
#define TONE_PART_SINE_LEN	(sizeof(partial_sine)\
			/sizeof(partial_sine[0]))

/*! Total length of sinusoid */
#define TONE_SINE_LEN	((TONE_PART_SINE_LEN*4)+4)

/*!
 * Generate a sine from the quarter-waveform.
 */
static int16_t tone_sine(uint8_t sample)
{
	/* Key points */
	if ((sample % (TONE_SINE_LEN/2)) == 0)
		/* Zero crossings */
		return 0;
	if (sample == TONE_SINE_LEN/4)
		/* Maximum */
		return INT16_MAX;
	if (sample == (3*TONE_SINE_LEN)/4)
		/* Minimum */
		return INT16_MIN;

	if (sample < TONE_SINE_LEN/4)
		/* First quarter of sine wave */
		return partial_sine[sample-1];

	if (sample < (TONE_SINE_LEN/2))
		/* Second quarter */
		return partial_sine[(TONE_SINE_LEN/2)-sample-1];
	if (sample < ((3*TONE_SINE_LEN)/4))
		/* Third quarter */
		return -partial_sine[(sample-3) % TONE_PART_SINE_LEN];
	if (sample < TONE_SINE_LEN)
		/* Final quarter */
		return -partial_sine[TONE_SINE_LEN-sample-1];
	/* We should not get here */
	return 0;
}

/*!
 * Re-set the tone generator.
 *
 * @param	tone_gen	Tone generator to reset.
 * @param	freq		Frequency in Hz, 0 = silence.
 * @param	duration	Duration in milliseconds.  0 to stop.
 */
void tone_reset(
	struct tone_gen_t* const tone_gen,
	uint16_t freq, uint16_t duration)
{
	if (freq)
		/* Compute the time step */
		tone_gen->step = (((2*freq*TONE_SINE_LEN) << TONE_SHIFT)
				/ ((2*TONE_FS) + 1) + 1);
	else
		/* DC tone == silence */
		tone_gen->step = 0;

	/* Compute remaining samples */
	tone_gen->remain = (uint16_t)(
			((uint32_t)(TONE_FS * duration)) / 1000);

	/* Initialise the sample counter */
	tone_gen->sample = 0;
}

/*!
 * Retrieve the next sample from the tone generator.
 * @param	tone_gen	Tone generator to update.
 */
int16_t tone_next(
	struct tone_gen_t* const tone_gen)
{
	if (!tone_gen)
		return 0;
	if (!tone_gen->remain)
		return 0;
	if (!tone_gen->step) {
		/* Special case, emit silence */
		tone_gen->remain--;
		return 0;
	}

	/* Compute sample index */
	uint16_t sample_int = ((tone_gen->sample) >> TONE_SHIFT)
				% TONE_SINE_LEN;

	/* Advance tone generator state */
	tone_gen->sample += tone_gen->step;
	tone_gen->remain--;

	return tone_sine(sample_int);
}

/*!
 * Retrieve the current time in milliseconds.
 */
uint32_t tone_msec(const struct tone_gen_t* const tone_gen)
{
	uint64_t ms = tone_gen->sample;
	ms *= 1000;
	ms /= TONE_FS;
	return ms >> TONE_SHIFT;
}
