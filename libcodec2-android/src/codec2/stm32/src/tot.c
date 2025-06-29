/*!
 * Time-out timer.
 *
 * This is a simple time-out timer for ensuring a maximum transmission
 * time is observed.  The time-out timer is configured with a total time
 * in "ticks", which get counted down in an interrupt.
 *
 * When the "warning" level is reached, a flag is repeatedly set permit
 * triggering of LEDs/sounds to warn the user that time is nearly up.
 *
 * Upon timeout, a separate flag is set to indicate timeout has taken
 * place.
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

#include "tot.h"

/*!
 * Reset the time-out timer.  This zeroes the counter and event flags.
 */
void tot_reset(struct tot_t * const tot)
{
	tot->event		= 0;
	tot->remaining		= 0;
	tot->warn_remain	= 0;
	tot->ticks		= 0;
}

/*!
 * Start the time-out timer ticking.
 */
void tot_start(struct tot_t * const tot, uint32_t tot_ticks,
		uint16_t warn_ticks)
{
	tot->event		= TOT_EVT_START;
	tot->warn_remain	= tot_ticks - warn_ticks;
	tot->remaining		= tot_ticks;
	tot->ticks		= tot->tick_period;
}

/*!
 * Update the time-out timer state.
 */
void tot_update(struct tot_t * const tot)
{
	if (!tot->event)
		/* We are not active */
		return;

	if (tot->event & TOT_EVT_DONE)
		/* We are done, do not process */
		return;

	if (tot->ticks)
		/* Wait for a tick to pass */
		return;

	/* One "tick" has passed */
	if (!tot->remaining) {
		/* Time-out reached, reset all flags except timeout */
		tot->event	|= TOT_EVT_TIMEOUT | TOT_EVT_DONE;
		return;
	} else {
		tot->remaining--;
	}

	if (!tot->warn_remain) {
		/* Warning period has passed */
		tot->event	|= TOT_EVT_WARN | TOT_EVT_WARN_NEXT;
		tot->warn_remain = tot->remain_warn_ticks;
	} else {
		tot->warn_remain--;
	}

	tot->ticks = tot->tick_period;
}
