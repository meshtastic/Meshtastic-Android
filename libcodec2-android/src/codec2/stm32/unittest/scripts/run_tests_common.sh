# This file must be "sourced" from a parent shell!
#
# run_tests_common.sh
#
# This is a collection of common variable settings for stm32 unit tests.
#
# The variable $SCRIPTS must be set when this is called.

if [ -z ${SCRIPTS+x} ]; then 
    echo "Error, run_tests_common.sh requires that \$SCRIPTS be set!"
    exit 1
    fi

#######################################
# Set default directories based on the parent of the SCRIPTS variable.
set -a 

#UNITTEST_BASE - Location of STM32 Unittests and files
UNITTEST_BASE="$( cd "$( dirname "${SCRIPTS}" )" >/dev/null && pwd )"

# STM32_BASE - Base directory of Stm32 files
STM32_BASE="$( cd "$( dirname "${UNITTEST_BASE}" )" >/dev/null && pwd )"

# STM32_BUILD - Build directory of Stm32 files
STM32_BUILD="${STM32_BASE}/build_stm32"

# UNITTEST_BIN - Location of STM32 unittest binaries
UNITTEST_BIN="${STM32_BUILD}/unittest/src"

# CODEC2_BASE - Base directory of Codec2
CODEC2_BASE="$( cd "$( dirname "${STM32_BASE}" )" >/dev/null && pwd )"

# CODEC2_BIN - Location of x86 utiliy programs for Codec2
CODEC2_BIN="${CODEC2_BASE}/build_linux/src"

# CODEC2_UTST - Location of x86 utiliy programs for Codec2 unittest
CODEC2_UTST="${CODEC2_BASE}/build_linux/unittest"

set +a 

#######################################
# Add directories to PATH
export PATH=${PATH}:${SCRIPTS}:${CODEC2_BIN}:${CODEC2_UTST}


#######################################
# Parse command line options
# Options (starting with "--") are stored in $ARGS.
# Non-options are taken as the test name, then as a test option (optional)
declare -A ARGS
unset TEST
unset TEST_OPT
for arg in "$@"; do
    if [[ ${arg} == --* ]] ; then ARGS[${arg}]=true
    else 
	if [ -z ${TEST+x} ]; then TEST=${arg}
	else TEST_OPT=${arg}
	fi
    fi
    done

# Prepend the common test name to the option if given
if [ -n "$TEST_OPT" ] ; then FULL_TEST_NAME="${TEST}_${TEST_OPT}"
else FULL_TEST_NAME="${TEST}"
fi

#######################################
# A function for setup

setup_common () {

    if [ ${ARGS[--clean]+_} ] ; then
        if [ -d "${1}" ] ; then rm -rf "${1}"; fi
        fi

    # Make run directory if needed
    if [ ! -d "${1}" ] ; then mkdir -p "${1}"; fi

    }
