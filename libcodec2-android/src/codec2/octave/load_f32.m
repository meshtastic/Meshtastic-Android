% load_f32.m
% David Rowe Jan 2019
%
% load up .f32 binary files from dump_data

function features = load_f32(fn, ncols)
  f=fopen(fn,"rb");
  features_lin=fread(f, 'float32');
  fclose(f);
  
  nrows = length(features_lin)/ncols;
  features = reshape(features_lin, ncols, nrows);
  features = features';
endfunction
