% load_raw.m
% David Rowe 7 Oct 2009

function s = load_raw(fn, len=Inf)
  fs=fopen(fn,"rb");
  s = fread(fs,len,"short");
  fclose(fs);
endfunction
