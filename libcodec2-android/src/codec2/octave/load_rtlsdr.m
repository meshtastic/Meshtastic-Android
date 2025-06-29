% load_rtlsdr.m
%
% David Rowe Oct 2015

function s = load_rtlsdr(fn)
  fs = fopen(fn,"rb");
  iq = fread(fs,Inf,"uchar");
  fclose(fs);
  l = length(iq);
  s =  iq(1:2:l) + j*iq(2:2:l);
endfunction
