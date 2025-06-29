% fsk_demod_file.m
% David Rowe May 2020
%
% Demodulate a file of off air samples and plot a bunch of internal
% states. Useful for debugging the FSK demod configuration

#{
   Sample usage to explore demodulator operation with a 100 bits/s 2FSK signal:

   $ cd ~/codec2/build_linux/src
   $ ./fsk_get_test_bits - 1000 | ./fsk_mod 2 8000 100 1000 1000 - ../../octave/fsk.s16
   $ octave --no-gui
   octave:1> fsk_demod_file("fsk.s16",format="s16",8000,100,2)

   Same thing but complex (single sided):
   
   $ ./fsk_get_test_bits - 1000 | ./fsk_mod 2 8000 100 1000 1000 - - | ./ch - fsk.cs16 --complexout
   octave:2> fsk_demod_file("fsk.cs16",format="cs16",8000,100,2)
#}

function fsk_demod_file(filename, format="s16", Fs=8000, Rs=50, M=2, P=8, avoid_dc = 1, max_secs=1E32)
  more off;
  fsk_lib;
  plot_en = 1;

  states = fsk_init(Fs, Rs, M, P);
  
  if strcmp(format,"s16")
    read_complex = 0; sample_size = 'int16'; shift_fs_on_4=0;
  elseif strcmp(format,"cs16") || strcmp(format,"iq16")
    read_complex = 1; sample_size = 'int16'; shift_fs_on_4=0;
    if avoid_dc states.fest_fmin = states.Rs*0.5; else states.fest_fmin = -Fs/2; end;
   states.fest_fmax = Fs/2; 
  elseif strcmp(format,"iqfloat")
    read_complex = 1; sample_size = 'float32'; shift_fs_on_4=0;
    if avoid_dc states.fest_fmin = states.Rs*0.5; else states.fest_fmin = -Fs/2; end;
    states.fest_fmax = Fs/2; 
  else
    printf("Error in format: %s\n", format);
    return;
  end

  fin = fopen(filename,"rb");
  if fin == -1 printf("Error opening file: %s\n",filename); return; end
  
  nbit = states.nbit;

  frames = 0;
  rx = []; rx_bits_log = []; norm_rx_timing_log = [];
  f_int_resample_log = []; EbNodB_log = []; ppm_log = [];
  f_log = []; Sf_log = [];
  
  % Extract raw bits from samples ------------------------------------------------------

  printf("demod of raw bits....\n");

  finished = 0; ph = 1; secs = 0;
  while (finished == 0)

    % read nin samples from input file

    nin = states.nin;
    if read_complex
      [sf count] = fread(fin, 2*nin, sample_size);
      if strcmp(sample_size, "uint8") sf = (sf - 127)/128; end
      sf = sf(1:2:end) + j*sf(2:2:end);
      count /= 2;
      if shift_fs_on_4
        % optional shift up in freq by Fs/4 to get into freq est range
        for i=1:count
          ph = ph*exp(j*pi/4);
          sf(i) *= ph;
        end
      end
    else
      [sf count] = fread(fin, nin, sample_size);
    end
    rx = [rx; sf];
    
    if count == nin
      frames++;

      % demodulate to stream of bits

      states = est_freq(states, sf, states.M);
      if states.freq_est_type == 'mask' states.f = states.f2; end
      [rx_bits states] = fsk_demod(states, sf);

      rx_bits_log = [rx_bits_log rx_bits];
      norm_rx_timing_log = [norm_rx_timing_log states.norm_rx_timing];
      f_int_resample_log = [f_int_resample_log abs(states.f_int_resample)];
      EbNodB_log = [EbNodB_log states.EbNodB];
      ppm_log = [ppm_log states.ppm];
      f_log = [f_log; states.f];
      Sf_log = [Sf_log; states.Sf'];
    else
      finished = 1;
    end

    secs += nin/Fs;
    if secs > max_secs finished=1; end
      
  end
  printf("frames: %d\n", frames);
  fclose(fin);

  if plot_en
    printf("plotting...\n");

    figure(1); clf;
    rx_nowave = rx(1000:length(rx)); % skip past wav header if it's a wave file
    subplot(211)
    plot(real(rx_nowave));
    title('input signal to demod (1 sec)')
    xlabel('Time (samples)');
    subplot(212);
    last = min(length(rx_nowave),Fs);
    Nfft = 2^(ceil(log2(last)));
    Rx = fft(rx_nowave(1:last).*hanning(last),Nfft);
    RxdB = 20*log10(abs(fftshift(Rx)));
    mx = 10*ceil(max(RxdB/10));
    f = -Nfft/2:Nfft/2-1;
    plot(f*Fs/Nfft, RxdB);
    axis([-Fs/2 Fs/2 mx-80 mx])
    xlabel('Frequency (Hz)');
    if length(rx) > Fs
      figure(2); Ndft=2^ceil(log2(Fs/10)); specgram(rx,Ndft,Fs);
    end
    figure(3); clf; plot(f_log,'+-'); axis([1 length(f_log) -Fs/2 Fs/2]); title('Tone Freq Estimates');    
    figure(4); clf; mesh(Sf_log(1:end,:)); title('Freq Est Sf over time');
    figure(5); clf; plot(f_int_resample_log','+'); title('Integrator outputs for each tone');
    figure(6); clf; plot(norm_rx_timing_log); axis([1 frames -0.5 0.5]); title('norm fine timing')
    figure(7); clf; plot(EbNodB_log); title('Eb/No estimate')
    figure(8); clf; plot(ppm_log); title('Sample clock (baud rate) offset in PPM');

  end

endfunction

