ENTRY(Reset_Handler)

/* Highest address of the user mode stack */
_estack = 0x20020000;    /* end of 128K RAM on AHB bus*/

/* Generate a link error if heap and stack don't fit into RAM */
_Min_Heap_Size = 0;      /* required amount of heap  */
_Min_Stack_Size = 0x400; /* required amount of stack */

/* Specify the memory areas */
MEMORY
{
  /* ISR vectors *must* be placed here as they get mapped to address 0 */
  VECTOR (rx)     : ORIGIN = 0x08000000, LENGTH = 16K
  /* Virtual EEPROM area, we use the remaining 16kB blocks for this. */
  EEPROM (rx)     : ORIGIN = 0x08004000, LENGTH = 48K
  /* The rest of flash is used for program data */
  FLASH (rx)      : ORIGIN = 0x08010000, LENGTH = 960K
  /* Backup memory area */
  BKPSRAM (rwx)   : ORIGIN = 0x40024000, LENGTH = 4K
  /* Memory area */
  RAM (rwx)       : ORIGIN = 0x20000000, LENGTH = 128K
  /* Core Coupled Memory */
  CCM (rwx)       : ORIGIN = 0x10000000, LENGTH = 64K
}

SECTIONS
{
  .isr_vector :
  {
    . = ALIGN(4);
    KEEP(*(.isr_vector))
    . = ALIGN(4);
  } >VECTOR


  .eeprom :
  {
    . = ALIGN(4);
    KEEP(*(.eeprom))   /* special section for persistent data */
    . = ORIGIN(EEPROM) + LENGTH(EEPROM) - 1;
    BYTE(0xFF)
    . = ALIGN(4);
  } >EEPROM = 0xff

  .bkpsram :
  {
    . = ALIGN(4);
    KEEP(*(.bkpsram))
    . = ALIGN(4);
  } > BKPSRAM

  .text :
  {
    . = ALIGN(4);
    *(.text)           /* .text sections (code) */
    *(.text*)          /* .text* sections (code) */
    *(.rodata)         /* .rodata sections (constants, strings, etc.) */
    *(.rodata*)        /* .rodata* sections (constants, strings, etc.) */
    *(.glue_7)         /* glue arm to thumb code */
    *(.glue_7t)        /* glue thumb to arm code */
    *(.eh_frame)

    KEEP (*(.init))
    KEEP (*(.fini))

    . = ALIGN(4);
    _etext = .;        /* define a global symbols at end of code */
    _exit = .;
  } >FLASH


   .ARM.extab   : { *(.ARM.extab* .gnu.linkonce.armextab.*) } >FLASH
    .ARM : {
    __exidx_start = .;
      *(.ARM.exidx*)
      __exidx_end = .;
    } >FLASH

  .preinit_array     :
  {
    PROVIDE_HIDDEN (__preinit_array_start = .);
    KEEP (*(.preinit_array*))
    PROVIDE_HIDDEN (__preinit_array_end = .);
  } >FLASH
  .init_array :
  {
    PROVIDE_HIDDEN (__init_array_start = .);
    KEEP (*(SORT(.init_array.*)))
    KEEP (*(.init_array*))
    PROVIDE_HIDDEN (__init_array_end = .);
  } >FLASH
  .fini_array :
  {
    PROVIDE_HIDDEN (__fini_array_start = .);
    KEEP (*(.fini_array*))
    KEEP (*(SORT(.fini_array.*)))
    PROVIDE_HIDDEN (__fini_array_end = .);
  } >FLASH

  /* used by the startup to initialize data */
  _sidata = .;

  /* Initialized data sections goes into RAM, load LMA copy after code */
  .data : AT ( _sidata )
  {
    . = ALIGN(4);
    _sdata = .;        /* create a global symbol at data start */
    *(.data)           /* .data sections */
    *(.data*)          /* .data* sections */

    . = ALIGN(4);
    _edata = .;        /* define a global symbol at data end */
  } >RAM

  /* Uninitialized data section */
  . = ALIGN(4);
  .bss :
  {
    /* This is used by the startup in order to initialize the .bss secion */
    _sbss = .;         /* define a global symbol at bss start */
    __bss_start__ = _sbss;
    *(.bss)
    *(.bss*)
    *(COMMON)

    . = ALIGN(4);
    _ebss = .;         /* define a global symbol at bss end */
    __bss_end__ = _ebss;
  } >RAM

  /* User_heap_stack section, used to check that there is enough RAM left */
  ._user_heap_stack :
  {
    . = ALIGN(4);
    PROVIDE ( end = . );
    PROVIDE ( _end = . );
    . = . + _Min_Heap_Size;
    . = . + _Min_Stack_Size;
    . = ALIGN(4);
  } >RAM

  .ccm (NOLOAD) : 
  {
 . = ALIGN(4);
 _sccmram = .;
 *(.ccm)
 . = ALIGN(4);      
	_eccmram = .;
  } >CCM
}
