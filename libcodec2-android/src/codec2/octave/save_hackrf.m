% save_hackrf.m
%
% David Rowe Aug 2020

function save_hackrf(fn,iq)
  l = length(iq);
  s = zeros(1,2*l);
  s(1:2:2*l) = real(iq);
  s(2:2:2*l) = imag(iq);
  fs = fopen(fn,"wb");
  fwrite(fs,s,"schar");
  fclose(fs);
endfunction
