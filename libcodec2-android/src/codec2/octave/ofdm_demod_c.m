% ofdm_demod_c.m
% David Rowe April 2018
%
% Plots data from The C OFDM demodulator ofdm_demod, in similar format to
% plots from Octave OFDM demodulator ofdm_rx.m
%
% Useful for of line analysis of a demod run

function ofdm_demod_c(filename, mode="700D")
  ofdm_lib;
  more off;

  % init modem

  config = ofdm_init_mode(mode);
  states = ofdm_init(config);
  ofdm_load_const;
  states.verbose = 0;

  load(filename);
  
  figure(1); clf; 
  plot(rx_np_log_c,'+');
  mx = 2*max(abs(rx_np_log_c));
  axis([-mx mx -mx mx]);
  title('Scatter');

  figure(2); clf;
  plot(phase_est_pilot_log_c(:,2:Nc),'g+', 'markersize', 5); 
  title('Phase Est');
  axis([1 length(phase_est_pilot_log_c) -pi pi]);  

  figure(3); clf;
  stem(timing_est_log_c)
  title('Timing Est');

  figure(4); clf;
  plot(foff_hz_log_c)
  mx = max(abs(foff_hz_log_c))+1;
  axis([1 max(length(foff_hz_log_c),2) -mx mx]);
  title('Fine Freq');
  ylabel('Hz')

  figure(5); clf;
  plot(snr_est_log_c);
  ylabel('SNR (dB)')
  title('SNR Estimates')
endfunction
