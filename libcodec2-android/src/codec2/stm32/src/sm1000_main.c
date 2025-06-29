/*---------------------------------------------------------------------------*\

  FILE........: sm1000_main.c
  AUTHOR......: David Rowe
  DATE CREATED: August 5 2014

  Main program for SM1000.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2014 David Rowe

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

#include "stm32f4_adc.h"
#include "stm32f4_dac.h"
#include "stm32f4_vrom.h"
#include "stm32f4_usart.h"
#include "freedv_api.h"
#include "codec2_fdmdv.h"
#include "sm1000_leds_switches.h"
#include "memtools.h"
#include <assert.h>
#include <stm32f4xx_gpio.h>
#include <stm32f4xx_rcc.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "sfx.h"
#include "sounds.h"
#include "morse.h"
#include "menu.h"
#include "tot.h"

#define VERSION         "V5"
#define FORTY_MS_16K    (0.04*16000)         /* 40ms of samples at 16 kHz */
#define FREEDV_NSAMPLES_16K (2*FREEDV_NSAMPLES)
#define CCM             (void*)0x10000000    /* start of 64k CCM memory   */
#define CCM_LEN         0x10000              /* start of 64k CCM memory   */
                                
#define MENU_LED_PERIOD  100
#define ANNOUNCE_DELAY  1500
#define HOLD_DELAY      1000
#define MENU_DELAY      1000

#define STATE_RX        0x00    /*!< Receive state: normal operation */
#define STATE_TX        0x10    /*!< Transmit state: normal operation */
#define STATE_RX_TOT    0x01    /*!< Receive state: after time-out */
#define STATE_MENU      0x20    /*!< Menu state: normal operation */

/*!
 * State machine states.  We consider our state depending on what events
 * are in effect at the start of the main() loop.  For buttons, we have
 * the following events:
 *
 *     PRESS:   Short-succession down-and-up event. (<1 second)
 *     DOWN:    Button press event with no release.
 *     UP:      Button release event.
 *     HOLD:    Button press for a minimum duration of 1 second without
 *              release.
 *
 * We also have some other state machines:
 *     TOT:
 *         IDLE:        No time-out event
 *         WARN:        Warning period reached event
 *         WARN_TICK:   Next warning tick due event
 *         TIMEOUT:     Cease transmit event
 *
 * We consider ourselves to be in one of a few finite states:
 *
 *     STATE_RX:    Normal receive state.
 *             Conditions:    !PTT.DOWN, !SELECT.HOLD
 *
 *             We receive samples via the TRX ADC and pass those
 *             to SPEAKER DAC after demodulation/filtering.
 *
 *             On SELECT.HOLD:      go to STATE_MENU
 *             On SELECT.PRESS:     next mode, stay in STATE_RX
 *             On BACK.PRESS:       prev mode, stay in STATE_RX
 *             On PTT.DOWN:         reset TOT, go to STATE_TX
 *
 *     STATE_TX:    Normal transmit state.
 *             Conditions:    PTT.DOWN, !TOT.TIMEOUT
 *
 *             We receive samples via the MIC ADC and pass those
 *             to TRX DAC after modulation/filtering.
 *
 *             On PTT.UP:           reset TOT, go to STATE_RX
 *             On TOT.WARN_TICK:    play tick noise,
 *                                  reset WARN_TICK event,
 *                                  stay in STATE_TX
 *             On TOT.TIMEOUT:      play timeout tune,
 *                                  reset TIMEOUT event
 *                                  go to STATE_RX_TOT.
 *
 *     STATE_RX_TOT:    Receive after time-out state.
 *             Conditions:    PTT.DOWN
 *
 *             We receive samples via the TRX ADC and pass those
 *             to SPEAKER DAC after demodulation/filtering.
 *
 *             On PTT.UP:           reset TOT, go to STATE_RX
 *
 *    STATE_MENU:   Menu operation state.  Operation is dictated by
 *                  the menu state machine, when we exit that state
 *                  machine, we return to STATE_RX.
 *
 *             On SELECT.HOLD:      select the current menu entry,
 *                                  if it is a submenu then make that the currnet level
 *             On SELECT.PRESS:     next entry in the current menu level
 *             On BACK.PRESS:       prev mode in the current menu level
 *             On BACK.HOLD:        go up to the previous menu 
 *             			    save any changes to NV memory
 *                                  This may exit the menu system
 *             On PTT.DOWN:         Exit menu system, do not save to NVM
 *
 *             See the "Menu data" section of this file for the menu structure
 *
 */
uint8_t core_state = STATE_RX;

#define MAX_MODES  4
#define ANALOG     0
#define DV1600     1
#define DV700D     2
#define DV700E     3

struct switch_t sw_select;  /*!< Switch driver for SELECT button */
struct switch_t sw_back;    /*!< Switch driver for BACK button */
struct switch_t sw_ptt;     /*!< Switch driver for PTT buttons */

struct tot_t tot;           /*!< Time-out timer */

unsigned int announceTicker = 0;
unsigned int menuLEDTicker = 0;
unsigned int menuTicker = 0;
unsigned int menuExit = 0;

uint32_t ms = 0;           /* increments once per ms */

/*!
 * User preferences
 */
static struct prefs_t {
    /*! Serial number */
    uint64_t serial;
    /*! Time-out timer period, in seconds increment */
    uint16_t tot_period;
    /*! Time-out timer warning period, in seconds increment */
    uint16_t tot_warn_period;
    /*! Menu frequency */
    uint16_t menu_freq;
    /*! Menu speed */
    uint8_t menu_speed;
    /*! Menu volume (attenuation) */
    uint8_t menu_vol;
    /*! Default operating mode */
    uint8_t op_mode;
} prefs;

/*! Preferences changed flag */
int prefs_changed = 0;

/*! Number of preference images kept */
#define PREFS_IMG_NUM       (2)
/*! Base ROM ID for preferences */
#define PREFS_IMG_BASE      (0)
/*! Minimum serial number */
#define PREFS_SERIAL_MIN    8
/*! Maximum serial number */
#define PREFS_SERIAL_MAX    UINT64_MAX

/*! Preference serial numbers, by slot */
static uint64_t prefs_serial[PREFS_IMG_NUM];

struct tone_gen_t tone_gen;
struct sfx_player_t sfx_player;
struct morse_player_t morse_player;

void SysTick_Handler(void);

/*! Menu item root */
static const struct menu_item_t menu_root;

#define MENU_EVT_NEXT   0x10    /*!< Increment the current item */
#define MENU_EVT_PREV   0x11    /*!< Decrement the current item */
#define MENU_EVT_SELECT 0x20    /*!< Select current item */
#define MENU_EVT_BACK   0x21    /*!< Go back one level */
#define MENU_EVT_EXIT   0x30    /*!< Exit menu */

/*!
 * Software-mix two 16-bit samples.
 */
int16_t software_mix(int16_t a, int16_t b) {
    int32_t s = a + b;
    if (s < INT16_MIN)
        return INT16_MIN;   /* Clip! */
    if (s > INT16_MAX)
        return INT16_MAX;   /* Clip! */
    return s;
}

/*! Compare current serial with oldest and newest */
void compare_prefs(int* const oldest, int* const newest, int idx)
{
    if (newest && prefs_serial[idx]) {
        if ((*newest < 0)
                || (prefs_serial[idx] > prefs_serial[*newest])
                || ((prefs_serial[idx] == PREFS_SERIAL_MIN)
                    && (prefs_serial[*newest] == PREFS_SERIAL_MAX)))
            *newest = idx;
    }

    if (oldest) {
        if ((*oldest < 0)
                || (!prefs_serial[idx])
                || (prefs_serial[idx] < prefs_serial[*oldest])
                || ((prefs_serial[idx] == PREFS_SERIAL_MAX)
                    && (prefs_serial[*oldest] == PREFS_SERIAL_MIN)))
            *oldest = idx;
    }
}

/*! Find oldest and newest images */
void find_prefs(int* const oldest, int* const newest)
{
    int i;
    if (newest) *newest = -1;
    if (oldest) *oldest = -1;
    for (i = 0; i < PREFS_IMG_NUM; i++)
        compare_prefs(oldest, newest, i);
}

/*! Load preferences from flash */
int load_prefs()
{
    struct prefs_t image[PREFS_IMG_NUM];
    int newest = -1;
    int i;

    /* Load all copies into RAM */
    for (i = 0; i < PREFS_IMG_NUM; i++) {
        int res = vrom_read(PREFS_IMG_BASE + i, 0,
                sizeof(image[i]), &image[i]);
        if (res == sizeof(image[i])) {
            prefs_serial[i] = image[i].serial;
            compare_prefs(NULL, &newest, i);
        } else {
            prefs_serial[i] = 0;
        }
    }

    if (newest < 0)
        /* No newest image was found */
        return -ENOENT;

    /* Load from the latest image */
    memcpy(&prefs, &image[newest], sizeof(prefs));
    return 0;
}

void print_prefs(struct prefs_t *prefs) {
    usart_printf("serial: %d\n", (int)prefs->serial);
    usart_printf("tot_period: %d\n", (int)prefs->tot_period);
    usart_printf("tot_warn_period: %d\n", (int)prefs->tot_warn_period);
    usart_printf("menu_freq: %d\n", (int)prefs->menu_freq);
    usart_printf("menu_speed: %d\n", (int)prefs->menu_speed);
    usart_printf("menu_vol: %d\n", (int)prefs->menu_vol);
    usart_printf("op_mode: %d\n", (int)prefs->op_mode);
    usart_printf("prefs_changed: %d\n", prefs_changed);
}

struct freedv *set_freedv_mode(int op_mode, int *n_samples) {
    struct freedv *f = NULL;
    switch(op_mode) {
    case ANALOG:
        usart_printf("Analog\n");
        *n_samples = FORTY_MS_16K/4;
        f = NULL;
        break;
    case DV1600:
        usart_printf("FreeDV 1600\n");
        f = freedv_open(FREEDV_MODE_1600);
        assert(f != NULL);
        *n_samples = freedv_get_n_speech_samples(f);
        break;
    case DV700D:
        usart_printf("FreeDV 700D\n");
        f = freedv_open(FREEDV_MODE_700D);
        assert(f != NULL);
        freedv_set_snr_squelch_thresh(f, -2.0);  /* squelch at -2.0 dB      */
        freedv_set_squelch_en(f, 1);
        freedv_set_eq(f, 1);                     /* equaliser on by default */
        
        /* Clipping and TXBPF nice to have for 700D. */
        freedv_set_clip(f, 1);
        freedv_set_tx_bpf(f, 1);
        
        *n_samples = freedv_get_n_speech_samples(f);
        break;
    case DV700E:
        usart_printf("FreeDV 700E\n");
        f = freedv_open(FREEDV_MODE_700E);
        assert(f != NULL);
        freedv_set_snr_squelch_thresh(f, 0.0);  /* squelch at 0.0 dB      */
        freedv_set_squelch_en(f, 1);
        freedv_set_eq(f, 1);                     /* equaliser on by default */

        /* Clipping and TXBPF needed for 700E. */
        freedv_set_clip(f, 1);
        freedv_set_tx_bpf(f, 1);

        *n_samples = freedv_get_n_speech_samples(f);
        break;
    }
    return f;
}

int process_core_state_machine(int core_state, struct menu_t  *menu, int *op_mode);

int main(void) {
    struct freedv *f;
    int            nin, nout, i;
    int            n_samples, n_samples_16k;

    usart_init(); usart_printf("SM1000 VERSION: %s\n", VERSION);
    usart_printf("SM1000 main()... stack 0x%x (%d)\n", &n_samples_16k, (uint32_t)0x2001ffff - (uint32_t)&n_samples_16k);
    memtools_find_unused(usart_printf);
    
    /* Menu data */
    struct menu_t   menu;

    /* Outgoing sample counter */
    int spk_nsamples = 0;

    /* Current runtime operation mode */
    int op_mode = ANALOG;

    /* init all the drivers for various peripherals */

    SysTick_Config(SystemCoreClock/1000); /* 1 kHz SysTick */
    sm1000_leds_switches_init();

    /* Enable CRC clock */
    RCC_AHB1PeriphClockCmd(RCC_AHB1Periph_CRC, ENABLE);

    /* Briefly open FreeDV 700D to determine buffer sizes we need
       (700D has the largest buffers) */

    f = freedv_open(FREEDV_MODE_700D);
    int n_speech_samples = freedv_get_n_speech_samples(f);
    int n_speech_samples_16k = 2*n_speech_samples;
    int n_modem_samples = freedv_get_n_max_modem_samples(f);
    int n_modem_samples_16k = 2*n_modem_samples;
    freedv_close(f); f = NULL;
    usart_printf("n_speech_samples: %d n_modem_samples: %d\n",
                 n_speech_samples, n_modem_samples);

    /* both speech and modem buffers will be about the same size, but
       choose the largest and add a little extra padding */
    if (n_speech_samples_16k > n_modem_samples_16k)
        n_samples_16k = n_speech_samples_16k;
    else
        n_samples_16k = n_modem_samples_16k;
    n_samples_16k += FORTY_MS_16K;
    usart_printf("n_samples_16k: %d storage for 4 FIFOs: %d bytes\n",
                 n_samples_16k, 4*2*n_samples_16k);

    /* Set up ADCs/DACs and their FIFOs, note storage is in CCM memory */
    short *pccm = CCM;
    usart_printf("pccm before dac/adc open: %p\n", pccm);
    n_samples = n_samples_16k/2;
    dac_open(DAC_FS_16KHZ, n_samples_16k, pccm, pccm+n_samples_16k);
    pccm += 2*n_samples_16k;
    adc_open(ADC_FS_16KHZ, n_samples_16k, pccm, pccm+n_samples_16k);
    pccm += 2*n_samples_16k;
    usart_printf("pccm after dac/adc open: %p\n", pccm);
    assert((void*)pccm < CCM+CCM_LEN);
    
    short          *adc16k = pccm; pccm += FDMDV_OS_TAPS_16K+n_samples_16k;
    short          *dac16k = pccm; pccm += n_samples_16k;
    short          adc8k[n_samples];
    short          dac8k[FDMDV_OS_TAPS_8K+n_samples];
    usart_printf("pccm after buffers: %p\n", pccm);
    assert((void*)pccm < CCM+CCM_LEN);

    /* clear buffers */
    for(i=0; i<FDMDV_OS_TAPS_16K+n_samples_16k; i++)
        adc16k[i] = 0; 
    for(i=0; i<n_samples_16k; i++)
        dac16k[i] = 0; 
    for(i=0; i<n_samples; i++)
        adc8k[i] = 0;
    for(i=0; i<FDMDV_OS_TAPS_8K+n_samples; i++)
        dac8k[i] = 0; 

    usart_printf("drivers initialised...stack: %p\n", memtools_sp);
    memtools_find_unused(usart_printf);
    
    /* put outputs into a known state */
    led_pwr(1); led_ptt(0); led_rt(0); led_err(0); not_cptt(1);

    if (!switch_back()) {
        /* Play tone to acknowledge, wait for release */
        tone_reset(&tone_gen, 1200, 1000);
        while(!switch_back()) {
            int dac_rem = dac2_free();
            if (dac_rem) {
                // TODO this might need fixing for larger FIFOs
                if (dac_rem > n_samples_16k)
                    dac_rem = n_samples_16k;

                for (i = 0; i < dac_rem; i++)
                    dac16k[i] = tone_next(&tone_gen);
                dac2_write(dac16k, dac_rem, 0);
            }
            if (!menuLEDTicker) {
                menuLEDTicker = MENU_LED_PERIOD;
                led_rt(LED_INV);
            }
        }

        /* Button released, do an EEPROM erase */
        for (i = 0; i < PREFS_IMG_NUM; i++)
            vrom_erase(i + PREFS_IMG_BASE);
    }
    led_rt(LED_OFF);
    tone_reset(&tone_gen, 0, 0);
    tot_reset(&tot);

    usart_printf("loading preferences from flash....\n");

    /* Try to load preferences from flash */
    if (load_prefs() < 0) {
        usart_printf("loading default preferences....\n");
        /* Fail!  Load defaults. */
        memset(&prefs, 0, sizeof(prefs));
        prefs.op_mode = ANALOG;
        prefs.menu_vol = 2;
        prefs.menu_speed = 60;  /* 20 WPM */
        prefs.menu_freq = 800;
        prefs.tot_period = 0; /* Disable time-out timer */
        prefs.tot_warn_period = 15;
    }
    print_prefs(&prefs);

    /* Set up time-out timer, 100msec ticks */
    tot.tick_period        = 100;
    tot.remain_warn_ticks  = 10;

    /* Clear out switch states */
    memset(&sw_select, 0, sizeof(sw_select));
    memset(&sw_back, 0, sizeof(sw_back));
    memset(&sw_ptt, 0, sizeof(sw_ptt));

    /* Clear out menu state */
    memset(&menu, 0, sizeof(menu));

    morse_player.freq = prefs.menu_freq;
    morse_player.dit_time = prefs.menu_speed;
    morse_player.msg = NULL;
    op_mode = prefs.op_mode;

    /* default op-mode */
    f = set_freedv_mode(op_mode, &n_samples);
    n_samples_16k = 2*n_samples;

    /* play VERSION and op mode at start-up.  Morse player can't queue
       so we assemble a concatenated string here */
    char startup_announcement[16];
    if (op_mode == ANALOG)
        snprintf(startup_announcement, 16, VERSION " ANA");
    else if (op_mode == DV1600)
        snprintf(startup_announcement, 16, VERSION " 1600");
    else if (op_mode == DV700D)
        snprintf(startup_announcement, 16, VERSION " 700D");
    else if (op_mode == DV700E)
        snprintf(startup_announcement, 16, VERSION " 700E");
    morse_play(&morse_player, startup_announcement);

    usart_printf("entering main loop...\n");
    uint32_t lastms = ms;    
    while(1) {
        /* Read switch states */
        switch_update(&sw_select,   (!switch_select()) ? 1 : 0);
        switch_update(&sw_back,     (!switch_back()) ? 1 : 0);
        switch_update(&sw_ptt,      (switch_ptt() || (!ext_ptt())) ? 1 : 0);

        /* Update time-out timer state */
        tot_update(&tot);

        /* iterate core state machine based on switch events */
        int prev_op_mode = op_mode;
        int prev_core_state = core_state;
        core_state = process_core_state_machine(core_state, &menu, &op_mode);

        /* Acknowledge switch events */
        switch_ack(&sw_select);
        switch_ack(&sw_back);
        switch_ack(&sw_ptt);

        /* if mode has changed, re-open freedv */
        if (op_mode != prev_op_mode) {
            usart_printf("Mode change prev_op_mode: %d op_mode: %d\n", prev_op_mode, op_mode);
            if (f) { freedv_close(f); } f = NULL;
            f = set_freedv_mode(op_mode, &n_samples);
            n_samples_16k = 2*n_samples;
            usart_printf("FreeDV f = 0x%x n_samples: %d n_samples_16k: %d\n", (int)f, n_samples, n_samples_16k);

            /* clear buffers */

            for(i=0; i<FDMDV_OS_TAPS_16K+n_samples_16k; i++)
                adc16k[i] = 0; 
            for(i=0; i<n_samples_16k; i++)
                dac16k[i] = 0; 
            for(i=0; i<n_samples; i++)
                adc8k[i] = 0;
            for(i=0; i<FDMDV_OS_TAPS_8K+n_samples; i++)
                dac8k[i] = 0; 
        }

        /* if we have moved from tx to rx reset sync state of rx so we re-start acquisition */
        if ((op_mode == DV1600) || (op_mode == DV700D) || (op_mode == DV700E))
            if ((prev_core_state == STATE_TX) && (core_state == STATE_RX))
                freedv_set_sync(f, FREEDV_SYNC_UNSYNC);
            
        /* perform signal processing based on core state */
        switch (core_state) {
            case STATE_MENU:
                if (!menuLEDTicker) {
                    led_pwr(LED_INV);
                    menuLEDTicker = MENU_LED_PERIOD;
                }
                break;
            case STATE_TX:
            /* Transmit -------------------------------------------------------------------------*/

                /* ADC2 is the SM1000 microphone, DAC1 is the modulator signal we send to radio tx */

                if (adc2_read(&adc16k[FDMDV_OS_TAPS_16K], n_samples_16k) == 0) {
                    GPIOE->ODR = (1 << 3);

                    /* clipping indicator */

                    led_err(0);
                    for (i=0; i<n_samples_16k; i++) {
                        if (abs(adc16k[FDMDV_OS_TAPS_16K+i]) > 28000)
                            led_err(1);
                    }

                    fdmdv_16_to_8_short(adc8k, &adc16k[FDMDV_OS_TAPS_16K], n_samples);

                    if (op_mode == ANALOG) {
                        for(i=0; i<n_samples; i++)
                            dac8k[FDMDV_OS_TAPS_8K+i] = adc8k[i];
                        fdmdv_8_to_16_short(dac16k, &dac8k[FDMDV_OS_TAPS_8K], n_samples);
                        dac1_write(dac16k, n_samples_16k, 0);
                    }
                    else {
                        freedv_tx(f, &dac8k[FDMDV_OS_TAPS_8K], adc8k);
                        for(i=0; i<n_samples; i++)
                            dac8k[FDMDV_OS_TAPS_8K+i] *= 0.398; /* 8dB back off from peak */
                        fdmdv_8_to_16_short(dac16k, &dac8k[FDMDV_OS_TAPS_8K], n_samples);
                        dac1_write(dac16k, n_samples_16k, 0);
                    }

                    led_ptt(1); led_rt(0); led_err(0); not_cptt(0);
                    GPIOE->ODR &= ~(1 << 3);
                }
                break;

            case STATE_RX:
            case STATE_RX_TOT:
                /* Receive --------------------------------------------------------------------------*/

                not_cptt(1);
                led_ptt(0);

                /* ADC1 is the demod in signal from the radio rx, DAC2 is the SM1000 speaker */

                if (op_mode == ANALOG) {
                    if (ms > lastms+5000) {
                        usart_printf("Analog\n");
                        lastms = ms;
                    }

                    if (adc1_read(&adc16k[FDMDV_OS_TAPS_16K], n_samples_16k) == 0) {
                        fdmdv_16_to_8_short(adc8k, &adc16k[FDMDV_OS_TAPS_16K], n_samples);
                        for(i=0; i<n_samples; i++)
                            dac8k[FDMDV_OS_TAPS_8K+i] = adc8k[i];
                        fdmdv_8_to_16_short(dac16k, &dac8k[FDMDV_OS_TAPS_8K], n_samples);
                        spk_nsamples = n_samples_16k;
                        led_rt(0); led_err(0);
                   }
                }
                else {
                    if (ms > lastms+5000) {
                        usart_printf("Digital Voice\n");
                        lastms = ms;
                    }
                    
                    /* 1600 or 700D/E DV mode */

                    nin = freedv_nin(f);
                    nout = nin;
                    freedv_set_total_bit_errors(f, 0);
                    if (adc1_read(&adc16k[FDMDV_OS_TAPS_16K], 2*nin) == 0) {
                        GPIOE->ODR = (1 << 3);
                        fdmdv_16_to_8_short(adc8k, &adc16k[FDMDV_OS_TAPS_16K], nin);
                        nout = freedv_rx(f, &dac8k[FDMDV_OS_TAPS_8K], adc8k);
                        fdmdv_8_to_16_short(dac16k, &dac8k[FDMDV_OS_TAPS_8K], nout);
                        spk_nsamples = 2*nout;
                        led_rt(freedv_get_sync(f)); led_err(freedv_get_total_bit_errors(f));
                        GPIOE->ODR &= ~(1 << 3);
                    }
                }
                break;
            default:
                break;
        }

        /* Write audio to speaker output */
        if (spk_nsamples || sfx_player.note || morse_player.msg) {
            /* Make a note of our playback position */
            int16_t* play_ptr = dac16k;

            if (!spk_nsamples)
                spk_nsamples = dac2_free();

            /*
             * There is audio to play on the external speaker.  If there
             * is a sound or announcement, software-mix it into the outgoing
             * buffer.
             */
            if (sfx_player.note) {
                int i;
                if (menu.stack_depth) {
                    /* Exclusive */
                    for (i = 0; i < spk_nsamples; i++)
                        dac16k[i] = sfx_next(&sfx_player) >> prefs.menu_vol;
                } else {
                    /* Software mix */
                    for (i = 0; i < spk_nsamples; i++)
                        dac16k[i] = software_mix(dac16k[i],
                                sfx_next(&sfx_player) >> prefs.menu_vol);
                }
                if (!sfx_player.note && morse_player.msg)
                    announceTicker = ANNOUNCE_DELAY;
            } else if (!announceTicker && morse_player.msg) {
                int i;
                if (menu.stack_depth) {
                    for (i = 0; i < spk_nsamples; i++)
                        dac16k[i] = morse_next(&morse_player) >> prefs.menu_vol;
                } else {
                    for (i = 0; i < spk_nsamples; i++)
                        dac16k[i] = software_mix(dac16k[i],
                                morse_next(&morse_player) >> prefs.menu_vol);
                }
            }

            while (spk_nsamples) {
                /* Get the number of samples to be played this time around */
                int n_rem = dac2_free();
                if (spk_nsamples < n_rem)
                    n_rem = spk_nsamples;
                /* Play the audio */
                dac2_write(play_ptr, n_rem, 0);
                spk_nsamples -= n_rem;
                play_ptr += n_rem;
            }

            /* Clear out buffer */
            memset(dac16k, 0, n_samples_16k*sizeof(short));
        }

    } /* while(1) ... */
}

/*
 * SysTick Interrupt Handler
 */

void SysTick_Handler(void)
{
    ms++;
    switch_tick(&sw_select);
    switch_tick(&sw_back);
    switch_tick(&sw_ptt);
    if (menuTicker > 0) {
        menuTicker--;
    }
    if (menuLEDTicker > 0) {
        menuLEDTicker--;
    }
    if (announceTicker > 0) {
        announceTicker--;
    }
    tot_tick(&tot);
}


int process_core_state_machine(int core_state, struct menu_t *menu, int *op_mode) {
    /* State machine updates */
    switch(core_state) {
    case STATE_RX:
        {
            uint8_t mode_changed = 0;

            if (!menuTicker) {
                if (menuExit) {
                    /* We've just exited a menu, wait for release of BACK */
                    if (switch_released(&sw_back))
                        menuExit = 0;
                } else if (switch_pressed(&sw_ptt)) {
                    /* Cancel any announcement if scheduled */
                    if (announceTicker && morse_player.msg) {
                        announceTicker = 0;
                        morse_play(&morse_player, NULL);
                    }
                    /* Start time-out timer if enabled */
                    if (prefs.tot_period)
                        tot_start(&tot, prefs.tot_period*10,
                                  prefs.tot_warn_period*10);
                    /* Enter transmit state */
                    core_state = STATE_TX;
                } else if (switch_pressed(&sw_select) > HOLD_DELAY) {
                    /* Enter the menu */
                    led_pwr(1); led_ptt(0); led_rt(0);
                    led_err(0); not_cptt(1);

                    menu_enter(menu, &menu_root);
                    menuTicker = MENU_DELAY;
                    core_state = STATE_MENU;
                    prefs_changed = 0;
                    usart_printf("Entering menu ...\n");
                    print_prefs(&prefs);

                } else if (switch_released(&sw_select)) {
                    /* Shortcut: change current mode */
                    *op_mode = (*op_mode + 1) % MAX_MODES;
                    mode_changed = 1;
                } else if (switch_released(&sw_back)) {
                    /* Shortcut: change current mode */
                    *op_mode = *op_mode - 1;
                    if (*op_mode < 0)
                    {
                        // Loop back around to the end of the mode list if we reach 0.
                        *op_mode = MAX_MODES - 1;
                    }
                    mode_changed = 1;
                }

                if (mode_changed) {
                    /* Announce the new mode */
                    if (*op_mode == ANALOG)
                        morse_play(&morse_player, "ANA");
                    else if (*op_mode == DV1600)
                        morse_play(&morse_player, "1600");
                    else if (*op_mode == DV700D)
                        morse_play(&morse_player, "700D");
                    else if (*op_mode == DV700E)
                        morse_play(&morse_player, "700E");
                    sfx_play(&sfx_player, sound_click);
                }
            }
        }
        break;
    case STATE_TX:
        {
            if (!switch_pressed(&sw_ptt)) {
                /* PTT released, leave transmit mode */
                tot_reset(&tot);
                core_state = STATE_RX;
            } else if (tot.event & TOT_EVT_TIMEOUT) {
                /* Time-out reached */
                sfx_play(&sfx_player, sound_death_march);
                tot.event &= ~TOT_EVT_TIMEOUT;
                core_state = STATE_RX_TOT;
            } else if (tot.event & TOT_EVT_WARN_NEXT) {
                /* Re-set warning flag */
                tot.event &= ~TOT_EVT_WARN_NEXT;
                /* Schedule a click tone */
                sfx_play(&sfx_player, sound_click);
            }
        }
        break;
    case STATE_RX_TOT:
        if (switch_released(&sw_ptt)) {
            /* PTT released, leave transmit mode */
            tot_reset(&tot);
            core_state = STATE_RX;
        }
        break;
    case STATE_MENU:
        if (!menuTicker) {
            /* We are in a menu */
            static uint8_t press_ack = 0;
            uint8_t save_settings = 0;

            if (press_ack == 1) {
                if ((sw_select.state == SW_STEADY)
                    && (!sw_select.sw))
                    press_ack = 0;
            } else if (press_ack == 2) {
                if ((sw_back.state == SW_STEADY)
                    && (!sw_back.sw))
                    press_ack = 0;
            } else {
                if (switch_pressed(&sw_select) > HOLD_DELAY) {
                    menu_exec(menu, MENU_EVT_SELECT);
                    press_ack = 1;
                    menuTicker = MENU_DELAY;
                } else if (switch_pressed(&sw_back) > HOLD_DELAY) {
                    menu_exec(menu, MENU_EVT_BACK);
                    press_ack = 2;
                    menuTicker = MENU_DELAY;

                    usart_printf("Leaving menu ... stack_depth: %d \n", menu->stack_depth);
                    print_prefs(&prefs);
                    if (!menu->stack_depth)
                        save_settings = prefs_changed;

                } else if (switch_released(&sw_select)) {
                    menu_exec(menu, MENU_EVT_NEXT);
                    menuTicker = MENU_DELAY;
                } else if (switch_released(&sw_back)) {
                    menu_exec(menu, MENU_EVT_PREV);
                    menuTicker = MENU_DELAY;
                } else if (switch_released(&sw_ptt)) {
                    while(menu->stack_depth > 0)
                        menu_exec(menu, MENU_EVT_EXIT);
                    sfx_play(&sfx_player, sound_returned);
                }

                /* If exited, put the LED back */
                if (!menu->stack_depth) {
                    menuLEDTicker = 0;
                    menuTicker = 0;
                    led_pwr(LED_ON);
                    morse_play(&morse_player, NULL);
                    menuExit = 1;
                    if (save_settings) {
                        int oldest = -1;
                        int res;
                        /* Copy the morse settings in */
                        prefs.menu_freq = morse_player.freq;
                        prefs.menu_speed = morse_player.dit_time;
                        /* make sure we have same op mode as power on prefs */
                        *op_mode = prefs.op_mode;
                        /* Increment serial number */
                        prefs.serial++;
                        /* Find the oldest image */
                        find_prefs(&oldest, NULL);
                        if (oldest < 0)
                            oldest = 0; /* No current image */

                        /* Write new settings over it */
                        usart_printf("vrom_write\n");
                        res = vrom_write(oldest + PREFS_IMG_BASE, 0,
                                         sizeof(prefs), &prefs);
                        if (res >= 0)
                            prefs_serial[oldest] = prefs.serial;
                    }
                    /* Go back to receive state */
                    core_state = STATE_RX;
                }
            }
        }
        break;
    default:
        break;
    }

    return core_state;
}


/* ---------------------------- Menu data ---------------------------
 *
 * MENU -
 * 	|- "MODE"       Select operating mode
 * 	|   |- "ANA"    - Analog
 * 	|   |- "DV1600" - FreeDV 1600 
 * 	|   |- "DV700D" - FreeDV 700D
 * 	|   |- "DV700E" - FreeDV 700E
 *      |
 * 	|- "TOT"        Timer Out Timer options
 * 	|   |- "TIME"   - Set timeout time (a sub menu)
 * 	|   |   |-        - SELECT.PRESS add 5 sec
 * 	|   |   |-        - BACK.PRESS subtracts 5 sec
 *      |   |
 * 	|   |- "WARN"   - Set warning time (a sub menu)
 * 	|   |   |-        - SELECT.PRESS add 5 sec
 * 	|   |   |-        - BACK.PRESS subtracts 5 sec
 *      | 
 * 	|- "UI"         UI (morse code announcments) parameters
 * 	|   |- "FREQ"   - Set tone
 * 	|   |   |-        - SELECT.PRESS add 50 Hz
 * 	|   |   |-        - BACK.PRESS subtracts 50 Hz
 *      |   |
 * 	|   |- "WPMQ"   - Set speed
 * 	|   |   |-        - SELECT.PRESS add 5 WPM
 * 	|   |   |-        - BACK.PRESS subtracts 5 WPM
 *      |   |
 * 	|   |- "VOL"    - Set volume
 * 	|   |   |-        - SELECT.PRESS -> quieter
 * 	|   |   |-        - BACK.PRESS -> louder
 */

/*!
 * Default handler for menu callback.
 */
static void menu_default_cb(struct menu_t* const menu, uint32_t event)
{
    /* Get the current menu item */
    const struct menu_item_t* item = menu_item(menu, 0);
    uint8_t announce = 0;

    switch(event) {
        case MENU_EVT_ENTERED:
            sfx_play(&sfx_player, sound_startup);
            /* Choose first item */
            menu->current = 0;
        case MENU_EVT_RETURNED:
            announce = 1;
            break;
        case MENU_EVT_NEXT:
            sfx_play(&sfx_player, sound_click);
            menu->current = (menu->current + 1) % item->num_children;
            announce = 1;
            break;
        case MENU_EVT_PREV:
            sfx_play(&sfx_player, sound_click);
            if (menu->current == 0)
            {
                menu->current = item->num_children - 1;
            }
            else
            {
                menu->current = menu->current - 1;
            }
            announce = 1;
            break;
        case MENU_EVT_SELECT:
            /* Enter the sub-menu */
            menu_enter(menu, item->children[menu->current]);
            break;
        case MENU_EVT_BACK:
            /* Exit the menu */
            sfx_play(&sfx_player, sound_returned);
        case MENU_EVT_EXIT:
            menu_leave(menu);
            break;
        default:
            break;
    }

    if (announce) {
        /* Announce the label of the selected child */
        morse_play(&morse_player,
                item->children[menu->current]->label);
    }
}

/* Root menu item forward declarations */
static const struct menu_item_t* menu_root_children[];
/* Root item definition */
static const struct menu_item_t menu_root = {
    .label          = "MENU",
    .event_cb       = menu_default_cb,
    .children       = menu_root_children,
    .num_children   = 3,
};

/* Child declarations */
static const struct menu_item_t menu_op_mode;
static const struct menu_item_t menu_tot;
static const struct menu_item_t menu_ui;
static const struct menu_item_t * menu_root_children[] = {
    &menu_op_mode,
    &menu_tot,
    &menu_ui,
};


/* Operation Mode menu forward declarations */
static void menu_op_mode_cb(struct menu_t* const menu, uint32_t event);
static struct menu_item_t const* menu_op_mode_children[];
/* Operation mode menu */
static const struct menu_item_t menu_op_mode = {
    .label          = "MODE",
    .event_cb       = menu_op_mode_cb,
    .children       = menu_op_mode_children,
    .num_children   = 4,
};
/* Children */
static const struct menu_item_t menu_op_mode_analog = {
    .label          = "ANA",
    .event_cb       = NULL,
    .children       = NULL,
    .num_children   = 0,
    .data           = {
        .ui         = ANALOG,
    },
};
static const struct menu_item_t menu_op_mode_dv1600 = {
    .label          = "1600",
    .event_cb       = NULL,
    .children       = NULL,
    .num_children   = 0,
    .data           = {
        .ui         = DV1600,
    },
};
static const struct menu_item_t menu_op_mode_dv700D = {
    .label          = "700D",
    .event_cb       = NULL,
    .children       = NULL,
    .num_children   = 0,
    .data           = {
        .ui         = DV700D,
    },
};
static const struct menu_item_t menu_op_mode_dv700E = {
    .label          = "700E",
    .event_cb       = NULL,
    .children       = NULL,
    .num_children   = 0,
    .data           = {
        .ui         = DV700E,
    },
};
static struct menu_item_t const* menu_op_mode_children[] = {
    &menu_op_mode_analog,
    &menu_op_mode_dv1600,
    &menu_op_mode_dv700D,
    &menu_op_mode_dv700E,
};
/* Callback function */
static void menu_op_mode_cb(struct menu_t* const menu, uint32_t event)
{
    const struct menu_item_t* item = menu_item(menu, 0);
    uint8_t announce = 0;

    switch(event) {
        case MENU_EVT_ENTERED:
            sfx_play(&sfx_player, sound_startup);
            /* Choose current item */
            switch(prefs.op_mode) {
                case DV1600:
                    menu->current = 1;
                    break;
                case DV700D:
                    menu->current = 2;
                    break;
                case DV700E:
                    menu->current = 3;
                    break;
                default:
                    menu->current = 0;
            }
        case MENU_EVT_RETURNED:
            /* Shouldn't happen, but we handle it anyway */
            announce = 1;
            break;
        case MENU_EVT_NEXT:
            sfx_play(&sfx_player, sound_click);
            menu->current = (menu->current + 1) % item->num_children;
            announce = 1;
            break;
        case MENU_EVT_PREV:
            sfx_play(&sfx_player, sound_click);
            if (menu->current == 0)
            {
                menu->current = item->num_children - 1;
            }
            else
            {
                menu->current = menu->current - 1;
            }
            announce = 1;
            break;
        case MENU_EVT_SELECT:
            /* Choose the selected mode */
            prefs.op_mode = item->children[menu->current]->data.ui;
            /* Play the "selected" tune and return. */
            sfx_play(&sfx_player, sound_startup);
            prefs_changed = 1;
            menu_leave(menu);
            break;
        case MENU_EVT_BACK:
            /* Exit the menu */
            sfx_play(&sfx_player, sound_returned);
        case MENU_EVT_EXIT:
            menu_leave(menu);
            break;
        default:
            break;
    }

    if (announce) {
        /* Announce the label of the selected child */
        morse_play(&morse_player,
                item->children[menu->current]->label);
    }
}


/* Time-out timer menu forward declarations */
static struct menu_item_t const* menu_tot_children[];
/* Operation mode menu */
static const struct menu_item_t menu_tot = {
    .label          = "TOT",
    .event_cb       = menu_default_cb,
    .children       = menu_tot_children,
    .num_children   = 2,
};
/* Children */
static const struct menu_item_t menu_tot_time;
static const struct menu_item_t menu_tot_warn;
static struct menu_item_t const* menu_tot_children[] = {
    &menu_tot_time,
    &menu_tot_warn,
};

/* TOT time menu forward declarations */
static void menu_tot_time_cb(struct menu_t* const menu, uint32_t event);
/* TOT time menu */
static const struct menu_item_t menu_tot_time = {
    .label          = "TIME",
    .event_cb       = menu_tot_time_cb,
    .children       = NULL,
    .num_children   = 0,
};

/* Callback function */
static void menu_tot_time_cb(struct menu_t* const menu, uint32_t event)
{
    uint8_t announce = 0;

    switch(event) {
        case MENU_EVT_ENTERED:
            sfx_play(&sfx_player, sound_startup);
            /* Get the current period */
            menu->current = prefs.tot_period;
        case MENU_EVT_RETURNED:
            /* Shouldn't happen, but we handle it anyway */
            announce = 1;
            break;
        case MENU_EVT_NEXT:
            sfx_play(&sfx_player, sound_click);
            /* Adjust the frequency up by 50 Hz */
            if (prefs.tot_period < 600)
                prefs.tot_period += 5;
            announce = 1;
            break;
        case MENU_EVT_PREV:
            sfx_play(&sfx_player, sound_click);
            if (prefs.tot_period > 0)
                prefs.tot_period -= 5;
            announce = 1;
            break;
        case MENU_EVT_SELECT:
            /* Play the "selected" tune and return. */
            sfx_play(&sfx_player, sound_startup);
            prefs_changed = 1;
            menu_leave(menu);
            break;
        case MENU_EVT_BACK:
            /* Restore the mode and exit the menu */
            sfx_play(&sfx_player, sound_returned);
        case MENU_EVT_EXIT:
            prefs.tot_period = menu->current;
            menu_leave(menu);
            break;
        default:
            break;
    }

    if (announce) {
        /* Render the text, thankfully we don't need re-entrancy */
        static char period[6];
        snprintf(period, 6, "%d", prefs.tot_period);
        /* Announce the period */
        morse_play(&morse_player, period);
    }
};

/* TOT warning time menu forward declarations */
static void menu_tot_warn_cb(struct menu_t* const menu, uint32_t event);
/* TOT warning time menu */
static const struct menu_item_t menu_tot_warn = {
    .label          = "WARN",
    .event_cb       = menu_tot_warn_cb,
    .children       = NULL,
    .num_children   = 0,
};

/* Callback function */
static void menu_tot_warn_cb(struct menu_t* const menu, uint32_t event)
{
    uint8_t announce = 0;

    switch(event) {
        case MENU_EVT_ENTERED:
            sfx_play(&sfx_player, sound_startup);
            /* Get the current period */
            if (prefs.tot_warn_period < prefs.tot_period)
                menu->current = prefs.tot_warn_period;
            else
                menu->current = prefs.tot_period;
        case MENU_EVT_RETURNED:
            /* Shouldn't happen, but we handle it anyway */
            announce = 1;
            break;
        case MENU_EVT_NEXT:
            sfx_play(&sfx_player, sound_click);
            /* Adjust the frequency up by 50 Hz */
            if (prefs.tot_warn_period < prefs.tot_period)
                prefs.tot_warn_period += 5;
            announce = 1;
            break;
        case MENU_EVT_PREV:
            sfx_play(&sfx_player, sound_click);
            if (prefs.tot_warn_period > 0)
                prefs.tot_warn_period -= 5;
            announce = 1;
            break;
        case MENU_EVT_SELECT:
            /* Play the "selected" tune and return. */
            sfx_play(&sfx_player, sound_startup);
            prefs_changed = 1;
            menu_leave(menu);
            break;
        case MENU_EVT_BACK:
            /* Restore the mode and exit the menu */
            sfx_play(&sfx_player, sound_returned);
        case MENU_EVT_EXIT:
            prefs.tot_warn_period = menu->current;
            menu_leave(menu);
            break;
        default:
            break;
    }

    if (announce) {
        /* Render the text, thankfully we don't need re-entrancy */
        static char period[6];
        snprintf(period, 6, "%d", prefs.tot_warn_period);
        /* Announce the period */
        morse_play(&morse_player, period);
    }
};

/* UI menu forward declarations */
static struct menu_item_t const* menu_ui_children[];
/* Operation mode menu */
static const struct menu_item_t menu_ui = {
    .label          = "UI",
    .event_cb       = menu_default_cb,
    .children       = menu_ui_children,
    .num_children   = 3,
};
/* Children */
static const struct menu_item_t menu_ui_freq;
static const struct menu_item_t menu_ui_speed;
static const struct menu_item_t menu_ui_vol;
static struct menu_item_t const* menu_ui_children[] = {
    &menu_ui_freq,
    &menu_ui_speed,
    &menu_ui_vol,
};

/* UI Frequency menu forward declarations */
static void menu_ui_freq_cb(struct menu_t* const menu, uint32_t event);
/* UI Frequency menu */
static const struct menu_item_t menu_ui_freq = {
    .label          = "FREQ",
    .event_cb       = menu_ui_freq_cb,
    .children       = NULL,
    .num_children   = 0,
};
/* Callback function */
static void menu_ui_freq_cb(struct menu_t* const menu, uint32_t event)
{
    uint8_t announce = 0;

    switch(event) {
        case MENU_EVT_ENTERED:
            sfx_play(&sfx_player, sound_startup);
            /* Get the current frequency */
            menu->current = morse_player.freq;
        case MENU_EVT_RETURNED:
            /* Shouldn't happen, but we handle it anyway */
            announce = 1;
            break;
        case MENU_EVT_NEXT:
            sfx_play(&sfx_player, sound_click);
            /* Adjust the frequency up by 50 Hz */
            if (morse_player.freq < 2000)
                morse_player.freq += 50;
            announce = 1;
            break;
        case MENU_EVT_PREV:
            sfx_play(&sfx_player, sound_click);
            if (morse_player.freq > 50)
                morse_player.freq -= 50;
            announce = 1;
            break;
        case MENU_EVT_SELECT:
            /* Play the "selected" tune and return. */
            sfx_play(&sfx_player, sound_startup);
            prefs_changed = 1;
            menu_leave(menu);
            break;
        case MENU_EVT_BACK:
            /* Restore the mode and exit the menu */
            sfx_play(&sfx_player, sound_returned);
        case MENU_EVT_EXIT:
            morse_player.freq = menu->current;
            menu_leave(menu);
            break;
        default:
            break;
    }

    if (announce) {
        /* Render the text, thankfully we don't need re-entrancy */
        static char freq[6];
        snprintf(freq, 6, "%d", morse_player.freq);
        /* Announce the frequency */
        morse_play(&morse_player, freq);
    }
};

/* UI Speed menu forward declarations */
static void menu_ui_speed_cb(struct menu_t* const menu, uint32_t event);
/* UI Speed menu */
static const struct menu_item_t menu_ui_speed = {
    .label          = "WPM",
    .event_cb       = menu_ui_speed_cb,
    .children       = NULL,
    .num_children   = 0,
};
/* Callback function */
static void menu_ui_speed_cb(struct menu_t* const menu, uint32_t event)
{
    uint8_t announce = 0;

    /* Get the current WPM */
    uint16_t curr_wpm = 1200 / morse_player.dit_time;

    switch(event) {
        case MENU_EVT_ENTERED:
            sfx_play(&sfx_player, sound_startup);
            /* Get the current frequency */
            menu->current = morse_player.dit_time;
        case MENU_EVT_RETURNED:
            /* Shouldn't happen, but we handle it anyway */
            announce = 1;
            break;
        case MENU_EVT_NEXT:
            sfx_play(&sfx_player, sound_click);
            /* Increment WPM by 5 */
            if (curr_wpm < 60)
                curr_wpm += 5;
            announce = 1;
            break;
        case MENU_EVT_PREV:
            sfx_play(&sfx_player, sound_click);
            if (curr_wpm > 5)
                curr_wpm -= 5;
            announce = 1;
            break;
        case MENU_EVT_SELECT:
            /* Play the "selected" tune and return. */
            sfx_play(&sfx_player, sound_startup);
            prefs_changed = 1;
            menu_leave(menu);
            break;
        case MENU_EVT_BACK:
            /* Restore the mode and exit the menu */
            sfx_play(&sfx_player, sound_returned);
        case MENU_EVT_EXIT:
            morse_player.dit_time = menu->current;
            menu_leave(menu);
            break;
        default:
            break;
    }

    if (announce) {
        /* Render the text, thankfully we don't need re-entrancy */
        static char wpm[5];
        snprintf(wpm, 5, "%d", curr_wpm);
        /* Set the new parameter */
        morse_player.dit_time = 1200 / curr_wpm;
        /* Announce the words per minute */
        morse_play(&morse_player, wpm);
    }
};

/* UI volume menu forward declarations */
static void menu_ui_vol_cb(struct menu_t* const menu, uint32_t event);
/* UI volume menu */
static const struct menu_item_t menu_ui_vol = {
    .label          = "VOL",
    .event_cb       = menu_ui_vol_cb,
    .children       = NULL,
    .num_children   = 0,
};
/* Callback function */
static void menu_ui_vol_cb(struct menu_t* const menu, uint32_t event)
{
    uint8_t announce = 0;

    switch(event) {
        case MENU_EVT_ENTERED:
            sfx_play(&sfx_player, sound_startup);
            /* Get the current volume */
            menu->current = prefs.menu_vol;
        case MENU_EVT_RETURNED:
            /* Shouldn't happen, but we handle it anyway */
            announce = 1;
            break;
        case MENU_EVT_NEXT:
            sfx_play(&sfx_player, sound_click);
            if (prefs.menu_vol > 0)
                prefs.menu_vol--;
            announce = 1;
            break;
        case MENU_EVT_PREV:
            sfx_play(&sfx_player, sound_click);
            if (prefs.menu_vol < 14)
                prefs.menu_vol++;
            announce = 1;
            break;
        case MENU_EVT_SELECT:
            /* Play the "selected" tune and return. */
            sfx_play(&sfx_player, sound_startup);
            menu_leave(menu);
            prefs_changed = 1;
            break;
        case MENU_EVT_BACK:
            /* Restore the mode and exit the menu */
            sfx_play(&sfx_player, sound_returned);
        case MENU_EVT_EXIT:
            prefs.menu_vol = menu->current;
            menu_leave(menu);
            break;
        default:
            break;
    }

    if (announce) {
        /* Render the text, thankfully we don't need re-entrancy */
        static char vol[5];
        snprintf(vol, 5, "%d", 15 - prefs.menu_vol);
        /* Announce the volume level */
        morse_play(&morse_player, vol);
    }
};
