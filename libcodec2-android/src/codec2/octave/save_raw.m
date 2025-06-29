% save_raw.m
% David Rowe 9 Feb 2015

function s = save_raw(fn,s)
  fs=fopen(fn,"wb");
  fwrite(fs,s,"short");
endfunction
