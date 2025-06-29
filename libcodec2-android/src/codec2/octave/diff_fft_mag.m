% Compare the magnitude spectrum of two int16 raw files
function diff_fft_mag(filename1, filename2, threshdB = -40, ignore=1000)
  % load samples and ignore any start up transients
  s1 = load_raw(filename1)'; 
  s1 = s1(ignore:end);
  s2 = load_raw(filename2)'; 
  s2 = s2(ignore:end);
  
  S1 = abs(fft(s1.*hanning(length(s1))'));
  S2 = abs(fft(s2.*hanning(length(s2))'));
  
  figure(1): clf;
  plot(20*log10(S1)); hold on; plot(20*log10(S2)); plot(20*log10(abs(S1-S2)),'r'); hold off;
  error = S1 - S2;
  error_energy = error*error';
  ratio = error_energy/(S1*S1');
  ratio_dB = 10*log10(ratio);
  printf("ratio_dB: %4.2f\n", ratio_dB);
  if ratio_dB < threshdB
    printf('PASS\n');
  else
    printf('FAIL\n');
  end
endfunction
