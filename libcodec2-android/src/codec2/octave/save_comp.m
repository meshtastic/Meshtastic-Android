% save_comp.m
% David Rowe Aug 2020

function save_comp(fn, iq)
  l = length(iq);
  s = zeros(1,2*l);
  s(1:2:2*l) = real(iq);
  s(2:2:2*l) = imag(iq);
  fs=fopen(fn,"wb");
  s = fwrite(fs,s,"float32");
  fclose(fs);
endfunction
