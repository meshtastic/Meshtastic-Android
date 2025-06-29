% cohpsk_demod_plot.m
% David Rowe May 2015
%
% Plot Octave outputs from cohpsk_demod, c2dec, to visualise whats going on
% when errors hit the system

#{
   $ ./cohpsk_get_test_bits - 5600 | ./cohpsk_mod - - | ./ch - - --No -40 | ./cohpsk_demod - - -o cohpsk_demod.txt | ./cohpsk_put_test_bits -
   octave> cohpsk_demod_plot("../build_linux/src/cohpsk_demod.txt")
#}
   
function cohpsk_demod_plot(fn)
  Nc=7; Nd=2; Ns=6;

  load(fn);

  Ncf = 100;     % number of codec frames to plot
  Nmf = Ncf/2;  % number of modem frames to plot
  Nms = Nmf*Ns; % number of modem symbols to plot

  figure(1)
  clf;

  % plot combined signals to show diversity gains

  combined = rx_symb_log_c(:,1:Nc);
  for d=2:Nd
    combined += rx_symb_log_c(:, (d-1)*Nc+1:d*Nc);
  end
  plot(combined*exp(j*pi/4)/sqrt(Nd),'+')
  title('Scatter');
  axis([-2 2 -2 2])

  figure(2)
  clf;
  subplot(211)
  plot(rx_phi_log_c(1:Nms,:))
  title('phase')
  axis([1 Nms -pi pi])
  subplot(212)
  plot(rx_amp_log_c(1:Nms,:))
  title('amplitude')
  axis([1 Nms 0 1])

  figure(3)
  subplot(211)
  plot(rx_timing_log_c)
  title('rx timing');
  subplot(212)
  stem(ratio_log_c)
  title('Sync ratio');

  figure(4)
  plot(f_est_log_c - 1500)
  title('freq offset est');
  axis([1 Nmf -50 50])

  figure(5)
  y = 1:Nms;
  x = 1:Nc*Nd;
  z = 20*log10(rx_amp_log_c(1:Nms,:));
  mesh(x,y,z);
  grid
  title('Channel Amplitude dB');
  a = min(min(z));
  b = max(max(z));
  axis([1 Nc*Nd 1 Nms a b])
end

