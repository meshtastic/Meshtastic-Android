% save_f32.m
% David Rowe Sep 2021
%
% save a matrix to .f32 binary files in row-major order

function save_f32(fn, m)
  f=fopen(fn,"wb");
  [r c] = size(m);
  mlinear = reshape(m', 1, r*c);
  fwrite(f, mlinear, 'float32');
  fclose(f);
endfunction
