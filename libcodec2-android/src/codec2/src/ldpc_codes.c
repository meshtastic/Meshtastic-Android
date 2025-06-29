/*
  FILE...: ldpc_codes.c
  AUTHOR.: David Rowe
  CREATED: July 2020

  Array of LDPC codes used for various Codec2 waveforms.
*/

#include <stdio.h>
#include <string.h>
#include "assert.h"
#include "ldpc_codes.h"
#include "interldpc.h"
#include "H_2064_516_sparse.h"
#include "HRA_112_112.h"
#include "HRAb_396_504.h"
#include "H_256_768_22.h"
#include "H_256_512_4.h"
#include "HRAa_1536_512.h"
#include "H_128_256_5.h"
#include "HRA_56_56.h"
#include "H_4096_8192_3d.h"
#include "H_16200_9720.h"
#include "H_1024_2048_4f.h"

struct LDPC ldpc_codes[] = {
    /* short rate 1/2 code for FreeDV 700D */
    {
        "HRA_112_112",
        HRA_112_112_MAX_ITER,
        0,
        1,
        1,
        HRA_112_112_CODELENGTH,
        HRA_112_112_NUMBERPARITYBITS,
        HRA_112_112_NUMBERROWSHCOLS,
        HRA_112_112_MAX_ROW_WEIGHT,
        HRA_112_112_MAX_COL_WEIGHT,
        (uint16_t *)HRA_112_112_H_rows,
        (uint16_t *)HRA_112_112_H_cols
    }
    ,
    /* short rate 1/2 code for FreeDV 700E */
    {
        "HRA_56_56",
        HRA_56_56_MAX_ITER,
        0,
        1,
        1,
        HRA_56_56_CODELENGTH,
        HRA_56_56_NUMBERPARITYBITS,
        HRA_56_56_NUMBERROWSHCOLS,
        HRA_56_56_MAX_ROW_WEIGHT,
        HRA_56_56_MAX_COL_WEIGHT,
        (uint16_t *)HRA_56_56_H_rows,
        (uint16_t *)HRA_56_56_H_cols
    },
    #ifndef __EMBEDDED__

    /* default Wenet High Alitiude Balloon rate 0.8 code */
    {
        "H_2064_516_sparse",
        MAX_ITER,
        0,
        1,
        1,
        CODELENGTH,
        NUMBERPARITYBITS,
        NUMBERROWSHCOLS,
        MAX_ROW_WEIGHT,
        MAX_COL_WEIGHT,
        (uint16_t *)H_2064_516_sparse_H_rows,
        (uint16_t *)H_2064_516_sparse_H_cols
    },

    /* rate 0.8 code used for FreeDV 2020 */
    {
        "HRAb_396_504",
        HRAb_396_504_MAX_ITER,
        0,
        1,
        1,
        HRAb_396_504_CODELENGTH,
        HRAb_396_504_NUMBERPARITYBITS,
        HRAb_396_504_NUMBERROWSHCOLS,
        HRAb_396_504_MAX_ROW_WEIGHT,
        HRAb_396_504_MAX_COL_WEIGHT,
        (uint16_t *)HRAb_396_504_H_rows,
        (uint16_t *)HRAb_396_504_H_cols
    },

    /* rate 1/3 code, works at raw BER of 14% */
    {
        "H_256_768_22",
        H_256_768_22_MAX_ITER,
        0,
        1,
        1,
        H_256_768_22_CODELENGTH,
        H_256_768_22_NUMBERPARITYBITS,
        H_256_768_22_NUMBERROWSHCOLS,
        H_256_768_22_MAX_ROW_WEIGHT,
        H_256_768_22_MAX_COL_WEIGHT,
        (uint16_t *)H_256_768_22_H_rows,
        (uint16_t *)H_256_768_22_H_cols
    },

    /* used for 4FSK/LLR experiments */
    {
        "H_256_512_4",
        H_256_512_4_MAX_ITER,
        0,
        1,
        1,
        H_256_512_4_CODELENGTH,
        H_256_512_4_NUMBERPARITYBITS,
        H_256_512_4_NUMBERROWSHCOLS,
        H_256_512_4_MAX_ROW_WEIGHT,
        H_256_512_4_MAX_COL_WEIGHT,
        (uint16_t *)H_256_512_4_H_rows,
        (uint16_t *)H_256_512_4_H_cols
    },

    /* used for 4FSK/LLR experiments */
    {
        "HRAa_1536_512",
        HRAa_1536_512_MAX_ITER,
        0,
        1,
        1,
        HRAa_1536_512_CODELENGTH,
        HRAa_1536_512_NUMBERPARITYBITS,
        HRAa_1536_512_NUMBERROWSHCOLS,
        HRAa_1536_512_MAX_ROW_WEIGHT,
        HRAa_1536_512_MAX_COL_WEIGHT,
        (uint16_t *)HRAa_1536_512_H_rows,
        (uint16_t *)HRAa_1536_512_H_cols
    },

    /* used for 4FSK/LLR experiments */
    {
        "H_128_256_5",
        H_128_256_5_MAX_ITER,
        0,
        1,
        1,
        H_128_256_5_CODELENGTH,
        H_128_256_5_NUMBERPARITYBITS,
        H_128_256_5_NUMBERROWSHCOLS,
        H_128_256_5_MAX_ROW_WEIGHT,
        H_128_256_5_MAX_COL_WEIGHT,
        (uint16_t *)H_128_256_5_H_rows,
        (uint16_t *)H_128_256_5_H_cols
    },

    /* Nice long code from Bill VK5DSP - useful for HF data */
    {
        "H_4096_8192_3d",
        H_4096_8192_3d_MAX_ITER,
        0,
        1,
        1,
        H_4096_8192_3d_CODELENGTH,
        H_4096_8192_3d_NUMBERPARITYBITS,
        H_4096_8192_3d_NUMBERROWSHCOLS,
        H_4096_8192_3d_MAX_ROW_WEIGHT,
        H_4096_8192_3d_MAX_COL_WEIGHT,
        (uint16_t *)H_4096_8192_3d_H_rows,
        (uint16_t *)H_4096_8192_3d_H_cols
    },

    /* Nice long code from Bill VK5DSP - useful for HF data */
    {
        "H_16200_9720",
        H_16200_9720_MAX_ITER,
        0,
        1,
        1,
        H_16200_9720_CODELENGTH,
        H_16200_9720_NUMBERPARITYBITS,
        H_16200_9720_NUMBERROWSHCOLS,
        H_16200_9720_MAX_ROW_WEIGHT,
        H_16200_9720_MAX_COL_WEIGHT,
        (uint16_t *)H_16200_9720_H_rows,
        (uint16_t *)H_16200_9720_H_cols
    },
     
    /* Another fine code from Bill VK5DSK - also useful for HF data */ 
    {
        "H_1024_2048_4f",
        H_1024_2048_4f_MAX_ITER,
        0,
        1,
        1,
        H_1024_2048_4f_CODELENGTH,
        H_1024_2048_4f_NUMBERPARITYBITS,
        H_1024_2048_4f_NUMBERROWSHCOLS,
        H_1024_2048_4f_MAX_ROW_WEIGHT,
        H_1024_2048_4f_MAX_COL_WEIGHT,
        (uint16_t *)H_1024_2048_4f_H_rows,
        (uint16_t *)H_1024_2048_4f_H_cols
    }
    #endif
};

int ldpc_codes_num(void) { return sizeof(ldpc_codes)/sizeof(struct LDPC); }

void ldpc_codes_list() {
    fprintf(stderr, "\n");
    for(int c=0; c<ldpc_codes_num(); c++) {
        int n =  ldpc_codes[c].NumberRowsHcols + ldpc_codes[c].NumberParityBits;
        int k = ldpc_codes[c].NumberRowsHcols;
        float rate = (float)k/n;
        fprintf(stderr, "%-20s rate %3.2f (%d,%d) \n", ldpc_codes[c].name, (double)rate, n, k);
    }
    fprintf(stderr, "\n");
}

int ldpc_codes_find(char name[]) {
    int code_index = -1;
    for(int c=0; c<ldpc_codes_num(); c++)
        if (strcmp(ldpc_codes[c].name, name) == 0)
            code_index = c;
    assert(code_index != -1); /* code not found */
    return code_index;
}

void ldpc_codes_setup(struct LDPC *ldpc, char name[]) {
    int code_index;
    code_index = ldpc_codes_find(name);
    assert(code_index != -1);
    memcpy(ldpc,&ldpc_codes[code_index], sizeof(struct LDPC));
    set_up_ldpc_constants(ldpc, ldpc->CodeLength, ldpc->NumberParityBits);
}
