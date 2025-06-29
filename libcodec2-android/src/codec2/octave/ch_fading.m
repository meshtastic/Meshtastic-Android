% ch_fading.m
% David Rowe
% April 2018

% function to write float fading samples for use by C programs

function ch_fading(raw_file_name, Fs, dopplerSpreadHz, len_samples)
  randn('seed',1);
  spread = doppler_spread(dopplerSpreadHz, Fs, len_samples);
  spread_2ms = doppler_spread(dopplerSpreadHz, Fs, len_samples);
  hf_gain = 1.0/sqrt(var(spread)+var(spread_2ms));
  printf("hf_gain: %f\n", hf_gain);
  
  % interleave real imag samples

  inter = zeros(1,len_samples*4);
  inter(1:4) = hf_gain;
  for i=1:len_samples
    inter(i*4+1) = real(spread(i));
    inter(i*4+2) = imag(spread(i));
    inter(i*4+3) = real(spread_2ms(i));
    inter(i*4+4) = imag(spread_2ms(i));
  end
  f = fopen(raw_file_name,"wb");
  fwrite(f, inter, "float32");
  fclose(f);
endfunction
