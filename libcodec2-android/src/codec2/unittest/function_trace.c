#include <stdio.h>
 
static FILE *fp_trace;
 
void
__attribute__ ((constructor))
trace_begin (void)
{
 fp_trace = fopen("function_trace.out", "w");
}
 
void
__attribute__ ((destructor))
trace_end (void)
{
 if(fp_trace != NULL) {
 fclose(fp_trace);
 }
}
 

void
__cyg_profile_func_enter (void *func,  void *caller)
{
 if(fp_trace != NULL) {
 fprintf(fp_trace, "e %p %p\n", func, caller);
 }
}
 
void
__cyg_profile_func_exit (void *func, void *caller)
{
 if(fp_trace != NULL) {
 fprintf(fp_trace, "x %p %p\n", func, caller);
 }
}
