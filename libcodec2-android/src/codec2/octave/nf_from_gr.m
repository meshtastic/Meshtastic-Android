% nf_from_gr.m
% David Rowe Mar 2016

#{
  Calculate NF from GNU Radio output samples in 
  ...IQIQ... (32 bit float) sample files
 
  1/ Take one sample with a -100dBm input carrier
  2/ Take another sample with no signal (just rx noise)
  3/ Set Fs, adjust st and en to use a chunk of spectrum without too
      many birdies.
 
  Gotchas:

  1/ Inspect Figure(1), the time domain plots.
  2/ Make sure plenty of ADC bits are being used with the noise-only sample,
     we don't want ADC quantisation noise to dominate.  Aim for about half
     full scale.
  3/ Also watch out for clipping on either sample.

#}

1;

function det_nf(p_filename, n_filename, title, Fs, st, en, Pin_dB, real_file=0)

  if real_file
    % real samples files of 16 bit shorts
    fs=fopen(p_filename,"rb");
    p = fread(fs,Inf,"short");
    fclose(fs);
    fs=fopen(n_filename,"rb");
    pn = fread(fs,Inf,"short");
    fclose(fs);
  else
    % GNU radio complex file input
    p =  load_comp(p_filename);
    pn = load_comp(n_filename);
  end

  % skip any start up transients
  
  tst = floor(0.1*Fs); ten = st + Fs - 1;
  P = fft(p(tst:ten));
  N = fft(pn(tst:ten));

  PdB = 20*log10(abs(P));
  NdB = 20*log10(abs(N));

  figure(1); clf;
  subplot(211); plot(real(p(tst:tst+floor(Fs*0.1))));
  subplot(212); plot(real(pn(tst:tst+floor(Fs*0.1))));
  
  figure(2); clf;
  subplot(211); plot(st:en, PdB(st:en));
  subplot(212); plot(st:en, NdB(st:en));

  #{ 
  ------------------------------------------------------------------------

  From Wikipedia: The Noise Figure is the difference in decibels
     (dB) between the noise output of the actual receiver to the noise
     output of an “ideal” receiver
  
  An ideal receiver would have an output noise power of:
 
    Nout_dB = 10log10(B) -174 + G_dB
 
  The -174 dBm/Hz figure is the thermal noise density at 25C, for
  every 1Hz of bandwidth your will get -174dBm of noise power.  It's
  the lower limit set by the laws of physics.  G_dB is the Rx gain. The
  10log10(B) term takes into account the bandwidth of the Rx.  A wider
  bandwidth means more total noise power.

  So if you have a 1Hz bandwidth, and a gain of 100dB, you would
  expect Nout_NdB = 0 -174 + 100 = -74dBm at the rx output with no
  signal.  If you have a 1000Hz bandwidth receiver you would have NdB_out
  = 20 -174 + 100 = -44dBm of noise power at the output.

  To determine Noise Figure:
    1) Sample the Rx output first with a test signal and then with noise only.
    2) Find the Rx gain using the test signal.
    3) Find the noise output power, then using the gain we can find the noise
       input power. 
    4) Normalise the noise input power to 1Hz noise bandwidth and
       compare to the thermal noise floor.

  ---------------------------------------------------------------------------- 
  #}

  % variance is the power of a sampled signal

  Pout_dB = 10*log10(var(P(st:en)));           % Rx output power with test signal
  G_dB    = Pout_dB - Pin_dB;                  % Gain of Rx                           
  Nout_dB = 10*log10(var(N(st:en)));           % Rx output power with noise
  Nin_dB  = Nout_dB - G_dB;                    % Rx input power with noise
  No_dB   = Nin_dB - 10*log10(en-st);          % Rx input power with noise in 1Hz bandwidth
  NF_dB   = No_dB + 174;                       % compare to thermal noise to get NF
  printf("%10s: Pin: %4.1f  Pout: %4.1f  G: %4.1f  NF: %3.1f dB\n", title, Pin_dB, Pout_dB, G_dB, NF_dB);
endfunction


% HackRF --------------------------

%p_filename = "~/Desktop/blogs/nf/hackrf_100dbm_4MHz.bin";
%n_filename = "~/Desktop/blogs/nf/hackrf_nosignal_4MHz.bin";
p_filename = "~/codec2-dev/build_linux/unittest/hackrf_100dbm_4MHz.bin";
n_filename = "~/codec2-dev/build_linux/unittest/hackrf_nosignal_4MHz.bin";
det_nf(p_filename, n_filename, "HackRF", 4E6, 180E3, 600E3, -100);

#{
% RTL-SDR --------------------------

p_filename = "~/Desktop/nf/neg100dBm_2MHz.bin";
n_filename = "~/Desktop/nf/nosignal_2MHz.bin";
det_nf(p_filename, n_filename, "RTL-SDR", 2E6, 100E3, 300E3, -100);

% AirSpy -------------------------

p_filename = "~/Desktop/nf/airspy_100dbm_2.5MSPS.bin";
n_filename = "~/Desktop/nf/airspy_nosig_2.5MSPS.bin";
det_nf(p_filename, n_filename, "AirSpy", 2.5E6, 100E3, 300E3, -100);

% Fun Cube Dongle Pro Plus -------------------------

p_filename = "~/Desktop/nf/fcdpp_100dbm_192khz.bin";
n_filename = "~/Desktop/nf/fcdpp_nosig_192khz.bin";
det_nf(p_filename, n_filename, "FunCube PP", 192E3, 25E3, 125E3, -100);
#}
