#ifndef _MENU_H
#define _MENU_H
/*!
 * Callback driven menu handler.
 *
 * The following is an implementation of a callback-driven menu system.
 * It supports arbitrary levels of menus (limited by size of return stack)
 * and supports arbitrary user events.
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

#define MENU_STACK_SZ	8	/*!< Size of the menu return stack */

#define MENU_EVT_ENTERED 0	/*!< Menu item has been entered */
#define MENU_EVT_RETURNED 1	/*!< We have returned from a submenu */

/*! Menu state structure */
struct menu_t {
	/*! The last seen menu item */
	const struct menu_item_t*		last;
	/*! Currently selected item index */
	uint32_t				current;
	/*! Current menu item stack */
	struct menu_stack_item_t {
		const struct menu_item_t*	item;
		uint32_t			index;
	} 					stack[MENU_STACK_SZ];
	/*! Present depth of the stack */
	uint8_t					stack_depth;
};

/*! Menu item structure */
struct menu_item_t {
	/*! Morse-code label for the menu item */
	const char*		label;
	/*! Event callback pointer for menu item */
	void			(*event_cb)(
			struct menu_t* const menu,
			uint32_t event);
	/*! Children of this menu item */
	const struct menu_item_t** const	children;
	uint32_t				num_children;
	/*! Arbitrary data */
	union menu_item_data_t {
		/*! Arbitrary pointer */
		const void*			p;
		/*! Arbitrary unsigned integer */
		uintptr_t			ui;
		/*! Arbitrary signed integer */
		intptr_t			si;
	}					data;
};

/*!
 * Return the Nth item on the stack.
 */
const struct menu_item_t* const menu_item(
		const struct menu_t* const menu, uint8_t index);

/*!
 * Enter a (sub)-menu.
 * @retval	-1	Stack is full
 * @retval	0	Success
 */
int menu_enter(struct menu_t* const menu,
		const struct menu_item_t* const item);

/*! Return from a (sub)-menu */
void menu_leave(struct menu_t* const menu);

/*!
 * Execute the callback for the current item with a user-supplied event.
 */
void menu_exec(struct menu_t* const menu, uint32_t event);

#endif
