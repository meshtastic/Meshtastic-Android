% load_comp.m
% David Rowe Sep 2015

function s = load_comp(fn)
  fs=fopen(fn,"rb");
  s = fread(fs,Inf,"float32");
  ls = length(s);
  s = s(1:2:ls) + j*s(2:2:ls);
endfunction
