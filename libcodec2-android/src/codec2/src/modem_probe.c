/*---------------------------------------------------------------------------*\

  FILE........: modem_probe.c
  AUTHOR......: Brady O'Brien
  DATE CREATED: 9 January 2016

  Library to easily extract debug traces from modems during development and
  verification

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2016 David Rowe

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

#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include "comp.h"
#include "octave.h"

#define TRACE_I 1
#define TRACE_F 2
#define TRACE_C 3


typedef struct probe_trace_info_s probe_trace_info;
typedef struct datlink_s datlink;

struct datlink_s{
	void * data;
	size_t len;
	datlink * next;
};

struct probe_trace_info_s{
	int type;
	char name[255];
	datlink * data;
	datlink * last;
	probe_trace_info *next;
};

static char *run = NULL;
static char *mod = NULL;
static probe_trace_info *first_trace = NULL;

/* Init the probing library */
void modem_probe_init_int(char *modname, char *runname){
	mod = malloc((strlen(modname)+1)*sizeof(char));
	run = malloc((strlen(runname)+1)*sizeof(char));
	strcpy(run,runname);
	strcpy(mod,modname);
}

/* 
 * Gather the data stored in the linked list into a single blob,
 * freeing links and buffers as it goes
 */
void * gather_data(datlink * d,size_t * len){
	size_t size = 0;
	datlink * cur = d;
	datlink * next;
	while(cur!=NULL){
		size += d->len;
		cur = cur->next;
	}
	cur = d;
	size_t i = 0;
	void * newbuf = malloc(size);
	
	while(cur!=NULL){
		memcpy(newbuf+i,cur->data,cur->len);
		i += cur->len;
		free(cur->data);
		next = cur->next;
		free(cur);
		cur = next;
	}
	*len = size;
	return newbuf;
}

/* Dump all of the traces into a nice octave-able dump file */
void modem_probe_close_int(){
	if(run==NULL)
		return;
	
	probe_trace_info *cur,*next;
	cur = first_trace;
	FILE * dumpfile = fopen(run,"w");
	void * dbuf;
	size_t len;
	
	while(cur != NULL){
		dbuf = gather_data(cur->data,&len);
		switch(cur->type){
			case TRACE_I:
				octave_save_int(dumpfile,cur->name,(int32_t*)dbuf,1,len/sizeof(int32_t));
	        	break;
			case TRACE_F:
				octave_save_float(dumpfile,cur->name,(float*)dbuf,1,len/sizeof(float),10);
				break;
			case TRACE_C:
				octave_save_complex(dumpfile,cur->name,(COMP*)dbuf,1,len/sizeof(COMP),10);
				break;
		}
		next = cur->next;
		free(cur);
		free(dbuf);
		cur = next;
	}
	
	fclose(dumpfile);
	free(run);
	free(mod);
}

/* Look up or create a trace by name */
probe_trace_info * modem_probe_get_trace(char * tracename){
	probe_trace_info *cur,*npti;
	
	/* Make sure probe session is open */
	if(run==NULL)
		return NULL;
	
	cur = first_trace;
	/* Walk through list, find trace with matching name */
	while(cur != NULL){
		/* We got one! */
		if(strcmp( cur->name, tracename) == 0){
			return cur;
		}
		cur = cur->next;
	}
	/* None found, open a new trace */
	
	npti = (probe_trace_info *) malloc(sizeof(probe_trace_info));
	npti->next = first_trace;
	npti->data = NULL;
	npti->last = NULL;
	strcpy(npti->name,tracename);
	first_trace = npti;
	
	return npti;
	
}


void modem_probe_samp_i_int(char * tracename,int32_t samp[],size_t cnt){
	probe_trace_info *pti;
	datlink *ndat;
	
	pti = modem_probe_get_trace(tracename);
	if(pti == NULL)
		return;
	
	pti->type = TRACE_I;
	
	ndat = (datlink*) malloc(sizeof(datlink));
	ndat->data = malloc(sizeof(int32_t)*cnt);
	
	ndat->len = cnt*sizeof(int32_t);
	ndat->next = NULL;
	memcpy(ndat->data,(void*)&(samp[0]),sizeof(int32_t)*cnt);
	
	if(pti->last!=NULL){
		pti->last->next = ndat;
		pti->last = ndat;
	} else {
		pti->data = ndat;
		pti->last = ndat;
	}
	
}

void modem_probe_samp_f_int(char * tracename,float samp[],size_t cnt){
	probe_trace_info *pti;
	datlink *ndat;
	
	pti = modem_probe_get_trace(tracename);
	if(pti == NULL)
		return;
	
	pti->type = TRACE_F;
	
	ndat = (datlink*) malloc(sizeof(datlink));
	ndat->data = malloc(sizeof(float)*cnt);
	
	ndat->len = cnt*sizeof(float);
	ndat->next = NULL;
	memcpy(ndat->data,(void*)&(samp[0]),sizeof(float)*cnt);
	
	if(pti->last!=NULL){
		pti->last->next = ndat;
		pti->last = ndat;
	} else {
		pti->data = ndat;
		pti->last = ndat;
	}
}
	
void modem_probe_samp_c_int(char * tracename,COMP samp[],size_t cnt){
	probe_trace_info *pti;
	datlink *ndat;
	
	pti = modem_probe_get_trace(tracename);
	if(pti == NULL)
		return;
	
	pti->type = TRACE_C;
	
	ndat = (datlink*) malloc(sizeof(datlink));
	ndat->data = malloc(sizeof(COMP)*cnt);
	
	ndat->len = cnt*sizeof(COMP);
	ndat->next = NULL;
	memcpy(ndat->data,(void*)&(samp[0]),sizeof(COMP)*cnt);
	
	if(pti->last!=NULL){
		pti->last->next = ndat;
		pti->last = ndat;
	} else {
		pti->data = ndat;
		pti->last = ndat;
	}
}
