% ldpc_gen_h_file.m
% David Rowe Sep 2015
%
% Create a C include file for use in mpdecode.c C cmd line LDPC decoder

function ldpc_gen_h_file(code_param, max_iterations, decoder_type, input_decoder_c, x_hat, detected_data)
       
  f = fopen(code_param.c_include_file, "wt");

  fprintf(f, "/*\n  FILE....: %s\n\n  Static arrays for LDPC codec, generated", code_param.c_include_file);
  fprintf(f, "\n  ldpc_gen_h_file.m.\n\n*/\n\n");
   
  fprintf(f,"#define NUMBERPARITYBITS %d\n", rows(code_param.H_rows));
  fprintf(f,"#define MAX_ROW_WEIGHT %d\n", columns(code_param.H_rows));
  fprintf(f,"#define CODELENGTH %d\n", code_param.symbols_per_frame);
  fprintf(f,"#define NUMBERROWSHCOLS %d\n", rows(code_param.H_cols));
  fprintf(f,"#define MAX_COL_WEIGHT %d\n", columns(code_param.H_cols));
  fprintf(f,"#define DEC_TYPE %d\n", decoder_type);
  fprintf(f,"#define MAX_ITER %d\n", max_iterations);

  fprintf(f,"\ndouble H_rows[] = {\n");
     
  % clock out 2D array to linear C array in row order ....

  [r c] = size(code_param.H_rows);
  for j=1:c
    for i=1:r
      fprintf(f, "%d", code_param.H_rows(i,j));
      if (i == r) && (j ==c)
        fprintf(f,"\n};\n");
      else
        fprintf(f,", ");
      end
    end
  end

  fprintf(f,"\ndouble H_cols[] = {\n");
  [r c] = size(code_param.H_cols);
  for j=1:c
    for i=1:r
      fprintf(f, "%d", code_param.H_cols(i,j));
      if (i == r) && (j == c)
        fprintf(f,"\n};\n");
      else
        fprintf(f,", ");
      end
    end
  end

  fprintf(f,"\ndouble input[] = {\n");
  for i=1:length(input_decoder_c)
    fprintf(f, "%.17g", input_decoder_c(i));
    if i == length(input_decoder_c)
      fprintf(f,"\n};\n");
    else
      fprintf(f,", ");            
    end
  end

  fprintf(f,"\nchar detected_data[] = {\n");
  for i=1:length(detected_data)
    fprintf(f, "%d", detected_data(i));
    if i == length(detected_data)
      fprintf(f,"\n};\n");
    else
      fprintf(f,", ");            
    end
  end

  fclose(f);
end

