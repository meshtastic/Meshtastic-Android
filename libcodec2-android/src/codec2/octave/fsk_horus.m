% fsk_horus.m
% David Rowe 10 Oct 2015
%
% Project Horus High Altitude Balloon (HAB) FSK demodulator
% See blog write up "All your modems are belong to us"
%   http://www.rowetel.com/?p=4629


fsk_lib;


% Basic modem set up for Horus

function states = fsk_horus_init(Fs,Rs,M=2)

  states = fsk_init(Fs,Rs,M);

  % Freq. estimator limits - keep these narrow to stop errors with low SNR 4FSK

  states.fest_fmin = 300;
  states.fest_fmax = 2800;
endfunction


% init rtty protocol specific states

function rtty = fsk_horus_init_rtty
  % Generate unque word that correlates against the ASCII "$$$$$" that
  % is at the start of each frame.
  % $ -> 36 decimal -> 0 1 0 0 1 0 0 binary 

  dollar_bits = fliplr([0 1 0 0 1 0 0]);
  mapped_db = 2*dollar_bits - 1;
  sync_bits = [1 1 0];
  mapped_sb = 2*sync_bits - 1;

  mapped = [mapped_db mapped_sb];
  npad   = rtty.npad   = 3;     % one start and two stop bits between 7 bit ascii chars
  nfield = rtty.nfield = 7;     % length of ascii character field

  rtty.uw = [mapped mapped mapped mapped mapped];
  rtty.uw_thresh = length(rtty.uw) - 2; % allow a few bit errors when looking for UW
  rtty.max_packet_len = 1000;
endfunction


% I think this is the binary protocol work from Jan 2016

function binary = fsk_horus_init_binary
  % Generate 16 bit "$$" unique word that is at the front of every horus binary
  % packet

  dollar_bits = [0 0 1 0 0 1 0 0];
  mapped_db = 2*dollar_bits - 1;

  binary.uw = [mapped_db mapped_db];
  binary.uw_thresh = length(binary.uw)-2;   % no bit errors when looking for UW

  binary.max_packet_len = 360;
endfunction


% Look for unique word and return index of first UW bit, or -1 if no
% UW found Sometimes there may be several matches, returns the
% position of the best match to UW.

function [uw_start best_corr corr] = find_uw(states, start_bit, rx_bits)
  uw = states.uw;

  mapped_rx_bits = 2*rx_bits - 1;
  best_corr = 0;
  uw_start = -1;
  found_uw = 0;

  % first first UW in buffer that exceeds threshold
  
  for i=start_bit:length(rx_bits) - length(uw)
    corr(i)  = mapped_rx_bits(i:i+length(uw)-1) * uw';
    if (found_uw == 0) && (corr(i) >= states.uw_thresh)
      uw_start = i;
      best_corr = corr;
      found_uw = 1;
    end
  end

endfunction


% Extract ASCII string from a Horus frame of bits

function [str crc_ok] = extract_ascii(states, rx_bits_buf, uw_loc)
  nfield = states.nfield;
  npad = states.npad;

  str = ""; str_dec = []; nstr = 0; ptx_crc = 1; rx_crc = "";
  endpacket = 0;

  st = uw_loc + length(states.uw);  % first bit of first char
  en = uw_loc + states.max_packet_len - nfield;
  %printf("\nst: %d en: %d len: %d\n", st, en, length(rx_bits_buf));

  for i=st:nfield+npad:en
    field = rx_bits_buf(i:i+nfield-1);
    ch_dec = field * (2.^(0:nfield-1))';

    % filter out unlikely characters that bit errors may introduce, and ignore \n

    if (ch_dec > 31) && (ch_dec < 91)
      str = [str char(ch_dec)];
    else 
      str = [str char(32)]; % space is "not sure"
    end
    nstr++;

    % build up array for CRC16 check

    if !endpacket && (ch_dec == 42)
      endpacket = 1; 
      rx_crc = crc16(str_dec);      % found a '*' so that's the end of the string for CRC calculations
      ptx_crc = nstr+1;             % this is where the transmit CRC starts
    end
    if !endpacket
      str_dec = [str_dec ch_dec];
    end
  end

  if (ptx_crc+3) <= length(str)
    tx_crc = str(ptx_crc:ptx_crc+3);
    crc_ok = strcmp(tx_crc, rx_crc);
  else
    crc_ok = 0;
  end

  str = str(1:ptx_crc-2);

endfunction


% Use soft decision information to find bits most likely in error.  I think
% this is some form of maximum likelihood decoding.

function [str crc_ok rx_bits_log_flipped] = sd_bit_flipping(states, rx_bits_log, rx_bits_sd_log, st, en);

  % force algorithm to ignore rs232 sync bits by marking them as "very likely", they have
  % no input to crc algorithm

  nfield = states.nfield;
  npad = states.npad;
  for i=st:nfield+npad:en
    rx_bits_sd_log(i+nfield:i+nfield+npad-1) = 1E6;
  end

  % make a list of bits with smallest soft decn values

  [dodgy_bits_mag dodgy_bits_index] = sort(abs(rx_bits_sd_log(st+length(states.uw):en)));
  dodgy_bits_index += length(states.uw) + st - 1;
  nbits = 6;
  ntries = 2^nbits;
  str = "";
  crc_ok = 0;
  
  % try various combinations of these bits

  for i=1:ntries-1
    error_mask = zeros(1, length(rx_bits_log));
    for b=1:nbits
      x = bitget(i,b);
      bit_to_flip = dodgy_bits_index(b);
      error_mask(bit_to_flip) = x;
      %printf("st: %d i: %d b: %d x: %d index: %d\n", st, i,b,x,bit_to_flip);
    end
    rx_bits_log_flipped = xor(rx_bits_log, error_mask);
    [str_flipped crc_ok_flipped] = extract_ascii(states, rx_bits_log_flipped, st);
    if crc_ok_flipped
      %printf("Yayy we fixed a packet by flipping with pattern %d\n", i);
      str = str_flipped;
      crc_ok = crc_ok_flipped;
    end
  end
endfunction


% Extract as many ASCII packets as we can from a great big buffer of bits

function npackets = extract_and_print_rtty_packets(states, rtty, rx_bits_log, rx_bits_sd_log)

  % use UWs to delimit start and end of data packets

  bit = 1;
  nbits = length(rx_bits_log);
  nfield = rtty.nfield;
  npad = rtty.npad;
  npackets = 0;
  
  uw_loc = find_uw(rtty, bit, rx_bits_log, states.verbose);
  
  while (uw_loc != -1)
    if bitand(states.verbose,0x8)
      printf("nbits: %d max_packet_len: %d uw_loc: %d\n", nbits, rtty.max_packet_len, uw_loc);
    end
    
    if (uw_loc + rtty.max_packet_len) < nbits
      % Now start picking out 7 bit ascii chars from frame.  It has some
      % structure so we can guess where fields are.  I hope we don't get
      % RS232 idle bits stuck into it anywhere, ie "bit fields" don't
      % change dynamically.

      % dump msg bits so we can use them as a test signal
      %msg = rx_bits_log(st:uw_loc-1);
      %save -ascii horus_msg.txt msg

      % simulate bit error for testing
      %rx_bits_log(st+200) = xor(rx_bits_log(st+100),1);
      %rx_bits_sd_log(st+100) = 0;
      
      [str crc_ok] = extract_ascii(rtty, rx_bits_log, uw_loc);

      if crc_ok == 0
        [str_flipped crc_flipped_ok rx_bits_log] = sd_bit_flipping(rtty, rx_bits_log, rx_bits_sd_log, uw_loc, uw_loc+rtty.max_packet_len); 
      end

      % update memory of previous packet, we use this to guess where errors may be
      if crc_ok || crc_flipped_ok
        states.prev_pkt = rx_bits_log(uw_loc+length(rtty.uw):uw_loc+rtty.max_packet_len);
      end

      if crc_ok
        str = sprintf("%s CRC OK", str);
        npackets++;
      else
        if crc_flipped_ok
          str = sprintf("%s fixed", str_flipped);
        else
          str = sprintf("%s CRC BAD", str);
        end
      end
      printf("%s\n", str);
    end

    % look for next packet

    bit = uw_loc + length(rtty.uw);
    uw_loc = find_uw(rtty, bit, rx_bits_log, states.verbose);

  endwhile
endfunction
 

% Extract as many binary packets as we can from a great big buffer of bits,
% and send them to the C decoder for FEC decoding.
% horus_l2 can be compiled a bunch of different ways.  You need to
% compile with:
%   codec2-dev/src$ gcc horus_l2.c -o horus_l2 -Wall -DDEC_RX_BITS -DHORUS_L2_RX

function corr_log = extract_and_decode_binary_packets(states, binary, rx_bits_log)
  corr_log = [];

  % use UWs to delimit start and end of data packets

  bit = 1;
  nbits = length(rx_bits_log);

  [uw_loc best_corr corr] = find_uw(binary, bit, rx_bits_log, states.verbose);
  corr_log = [corr_log corr];
  
  while (uw_loc != -1)

    if (uw_loc+binary.max_packet_len) < nbits
      % printf("uw_loc: %d best_corr: %d\n", uw_loc, best_corr);

      % OK we have a packet delimited by two UWs.  Lets convert the bit
      % stream into bytes and save for decoding

      pin = uw_loc;    
      for i=1:45
        rx_bytes(i) = rx_bits_log(pin:pin+7) * (2.^(7:-1:0))';
        pin += 8;
        %printf("%d 0x%02x\n", i, rx_bytes(i));
      end

      f=fopen("horus_rx_bits_binary.bin","wb");
      fwrite(f, rx_bytes, "uchar");
      fclose(f);

      % optionally write packet to disk to use as horus_tx_bits_binary.txt
      f=fopen("horus_rx_bits_binary.txt","wt");
      for i=uw_loc:uw_loc+45*8-1
        fprintf(f, "%d ", rx_bits_log(i));
      end
      fclose(f);

      system("../src/horus_l2");  % compile instructions above
    end

    bit = uw_loc + length(binary.uw);
    [uw_loc best_corr corr] = find_uw(binary, bit, rx_bits_log, states.verbose);
    corr_log = [corr_log corr];
   
  endwhile
  
endfunction
 

% simulation of tx and rx side, add noise, channel impairments ----------------------
%
% test_frame_mode     Description
% 1                   BER testing using known test frames
% 2                   random bits
% 3                   repeating sequence of all symbols
% 4                   Horus RTTY
% 5                   Horus Binary
% 6                   Horus High Speed: A 8x oversampled modem, e.g. Fs=9600, Rs=1200
%                     which is the same as Fs=921600 Rs=115200
%                     Uses packet based BER counter

function run_sim(test_frame_mode, M=2, frames = 10, EbNodB = 100, filename="fsk_horus.raw")
  timing_offset = 0.0; % see resample() for clock offset below
  fading = 0;          % modulates tx power at 2Hz with 20dB fade depth, 
                       % to simulate balloon rotating at end of mission
  df     = 0;          % tx tone freq drift in Hz/s
  dA     = 1;          % amplitude imbalance of tones (note this affects Eb so not a gd idea)

  more off
  rand('state',1); 
  randn('state',1);

  % ----------------------------------------------------------------------

  % sm2000 config ------------------------
  %states = fsk_horus_init(96000, 1200);
  %states.f1_tx = 4000;
  %states.f2_tx = 5200;

  if test_frame_mode < 4
    % horus rtty config ---------------------
    states = fsk_horus_init(8000, 50, M);
  end

  if test_frame_mode == 4
    % horus rtty config ---------------------
    states = fsk_horus_init(8000, 100, 2);
    states.tx_bits_file = "horus_payload_rtty.txt"; % Octave file of bits we FSK modulate
    rtty = fsk_horus_init_rtty;
    states.ntestframebits = rtty.max_packet_len;
  end
                               
  if test_frame_mode == 5
    % horus binary config ---------------------
    states = fsk_horus_init(8000, 100, 4);
    binary = fsk_horus_init_binary;
    states.tx_bits_file = "horus_tx_bits_binary.txt"; % Octave file of bits we FSK modulate
    states.ntestframebits = binary.max_packet_len;
  end

  if test_frame_mode == 6
    % horus high speed ---------------------
    states = fsk_horus_init(Fs=9600, Rs=1200, M=2, P=8, nsym=16);
    states.tx_bits_file = "horus_high_speed.bin";
  end

  % Tones must be at least Rs apart for ideal non-coherent FSK

  states.ftx = 900 + 2*states.Rs*(1:states.M);
  states.tx_tone_separation = states.ftx(2) - states.ftx(1);
  
  % ----------------------------------------------------------------------

  states.verbose = 0x1;
  M = states.M;
  N = states.N;
  P = states.P;
  Rs = states.Rs;
  nsym = states.nsym;
  nbit = states.nbit;
  Fs = states.Fs;
  states.df(1:M) = df;
  states.dA(1:M) = dA;

  % optional noise.  Useful for testing performance of waveforms from real world modulators

  EbNo = 10^(EbNodB/10);
  variance = states.Fs/(states.Rs*EbNo*states.bitspersymbol);

  % set up tx signal with payload bits based on test mode

  if (test_frame_mode == 1)
    % test frame of bits, which we repeat for convenience when BER testing
    states.ntestframebits = states.nbit;
    test_frame = round(rand(1, states.ntestframebits));
    tx_bits = [];
    for i=1:frames+1
      tx_bits = [tx_bits test_frame];
    end
  end

  if test_frame_mode == 2
    % random bits, just to make sure sync algs work on random data
    tx_bits = round(rand(1, states.nbit*(frames+1)));
  end

  if test_frame_mode == 3
    % repeating sequence of all symbols
    % great for initial test of demod if nothing else works, 
    % look for this pattern in rx_bits
    if M == 2
       % ...10101...
      tx_bits = zeros(1, states.nbit*(frames+1));
      tx_bits(1:2:length(tx_bits)) = 1;
    else
      % repeat each possible 4fsk symbol
      pattern = [0 0 0 1 1 0 1 1];
      %pattern = [0 0 0 1 1 1 1 0];
      nrepeats = states.nbit*(frames+1)/length(pattern);
      tx_bits = [];
      for b=1:nrepeats
        tx_bits = [tx_bits pattern];
      end   
      %tx_bits = zeros(1, states.nbit*(frames+1));
    end
  end
 
  if (test_frame_mode == 4) || (test_frame_mode == 5)

    % load up a horus msg from disk and modulate that

    test_frame = load(states.tx_bits_file);
    ltf = length(test_frame);
    ntest_frames = ceil((frames+1)*nbit/ltf);
    printf("Generating %d test packets\n", ntest_frames);

    % 1 second of random bits to let estimators lock on

    preamble = round(rand(1,states.Rs));

    tx_bits = preamble;    
    for i=1:ntest_frames
      tx_bits = [tx_bits test_frame];
    end

    % a packet len of random bits at end fill buffers to deocode final packet

    if test_frame_mode == 4
      postamble = round(rand(1,rtty.max_packet_len));
    else
      postamble = round(rand(1,binary.max_packet_len));
    end
    tx_bits = [tx_bits postamble];
  end

  if test_frame_mode == 6
    states.verbose += 0x4;
    ftmp = fopen(states.tx_bits_file, "rb"); test_frame = fread(ftmp,Inf,"char")'; fclose(ftmp);
    states.ntestframebits = length(test_frame);
    printf("length test frame: %d\n", states.ntestframebits);
    %test_frame = rand(1,states.ntestframebits) > 0.5;

    tx_bits = [];
    for i=1:frames+1
      tx_bits = [tx_bits test_frame];
    end
  end

  tx = fsk_mod(states, tx_bits);

  %tx = resample(tx, 1000, 1001); % simulated 1000ppm sample clock offset

  if fading
     ltx = length(tx);
     tx = tx .* (1.1 + cos(2*pi*2*(0:ltx-1)/Fs))'; % min amplitude 0.1, -20dB fade, max 3dB
  end

  noise = sqrt(variance)*randn(length(tx),1);
  rx    = tx + noise;
  printf("SNRdB meas: %4.1f\n", 10*log10(var(tx)/var(noise)));

  % dump simulated rx file

  ftx=fopen(filename,"wb"); rxg = rx*1000; fwrite(ftx, rxg, "short"); fclose(ftx);

  timing_offset_samples = round(timing_offset*states.Ts);
  st = 1 + timing_offset_samples;
  rx_bits_buf = zeros(1,nbit+states.ntestframebits);
  x_log = [];
  timing_nl_log = [];
  norm_rx_timing_log = [];
  f_int_resample_log = [];
  f_log = [];
  EbNodB_log = [];
  rx_bits_log = [];
  rx_bits_sd_log = [];

  % main loop ---------------------------------------------------------------

  run_frames = floor(length(rx)/N)-1;
  for f=1:run_frames

    % extract nin samples from input stream

    nin = states.nin;
    en = st + states.nin - 1;

    if en < length(rx) % due to nin variations its possible to overrun buffer
      sf = rx(st:en);
      st += nin;

      % demodulate to stream of bits

      states = est_freq(states, sf, states.M);
      %states.f = 900 + 2*states.Rs*(1:states.M);
      %states.f = [1200 1400 1600 1800];
      [rx_bits states] = fsk_demod(states, sf);

      rx_bits_buf(1:states.ntestframebits) = rx_bits_buf(nbit+1:states.ntestframebits+nbit);
      rx_bits_buf(states.ntestframebits+1:states.ntestframebits+nbit) = rx_bits;
      %rx_bits_buf(1:nbit) = rx_bits_buf(nbit+1:2*nbit);
      %rx_bits_buf(nbit+1:2*nbit) = rx_bits;

      rx_bits_log = [rx_bits_log rx_bits];
      rx_bits_sd_log = [rx_bits_sd_log states.rx_bits_sd];

      norm_rx_timing_log = [norm_rx_timing_log states.norm_rx_timing];
      x_log = [x_log states.x];
      timing_nl_log = [timing_nl_log states.timing_nl];
      f_int_resample_log = [f_int_resample_log abs(states.f_int_resample(:,:))];
      f_log = [f_log; states.f];
      EbNodB_log = [EbNodB_log states.EbNodB];

      if test_frame_mode == 1
        states = ber_counter(states, test_frame, rx_bits_buf);
      end
      if test_frame_mode == 6
        states = ber_counter_packet(states, test_frame, rx_bits_buf);
      end
   end
  end

  % print stats, count errors, decode packets  ------------------------------------------

  if (test_frame_mode == 1) || (test_frame_mode == 6)
    printf("frames: %d EbNo: %3.2f Tbits: %d Terrs: %d BER %4.3f\n", frames, EbNodB, states.Tbits,states. Terrs, states.Terrs/states.Tbits);
  end

  if test_frame_mode == 4
    npackets = extract_and_print_rtty_packets(states, rtty, rx_bits_log, rx_bits_sd_log);
    printf("Received %d packets\n", npackets);
  end

  if test_frame_mode == 5
    extract_and_decode_binary_packets(states, binary, rx_bits_log);
  end

  figure(1);
  plot(f_int_resample_log','+')
  hold off;

  figure(2)
  clf
  m = max(abs(x_log));
  plot(x_log,'+')
  axis([-m m -m m])
  title('fine timing metric')

  figure(3)
  clf
  subplot(211)
  plot(norm_rx_timing_log);
  axis([1 run_frames -1 1])
  title('norm fine timing')
  subplot(212)
  plot(states.nerr_log)
  title('num bit errors each frame')

  figure(4)
  clf
  subplot(211)
  one_sec_rx = rx(1:min(Fs,length(rx)));
  plot(one_sec_rx)
  title('rx signal at demod input')
  subplot(212)
  plot(abs(fft(one_sec_rx)))

  figure(5)
  clf
  plot(f_log,'+')
  title('tone frequencies')
  axis([1 run_frames 0 Fs/2])

  figure(6)
  clf
  plot(EbNodB_log);
  title('Eb/No estimate')

  figure(7)
  clf
  subplot(211)
  X = abs(fft(timing_nl_log));
  plot(X(1:length(X)/2))
  subplot(212)
  plot(abs(timing_nl_log(1:100)))

 endfunction


% ---------------------------------------------------------------------
% demodulate from a user-supplied file
% ---------------------------------------------------------------------

function rx_bits_log = demod_file(filename, test_frame_mode=4, noplot=0, EbNodB=100, max_frames=1E32)
  fin = fopen(filename,"rb"); 
  more off;
  read_complex = 0; sample_size = 'int16'; shift_fs_on_4 = 0;
  max_frames
  
  if test_frame_mode == 4
    % horus rtty config ---------------------
    states = fsk_horus_init(8000, 100, 2);
    rtty = fsk_horus_init_rtty;
    states.ntestframebits = rtty.max_packet_len;
  end
                               
  if test_frame_mode == 5
    % horus binary config ---------------------
    states = fsk_horus_init(8000, 100, 4);
    binary = fsk_horus_init_binary;
    states.ntestframebits = binary.max_packet_len;
  end

  states.verbose = 0x1 + 0x8;

  if test_frame_mode == 6
    % Horus high speed config --------------
    states = fsk_horus_init(Fs=9600, Rs=1200, M=2,  P=8, nsym=16);
    states.tx_bits_file = "horus_high_speed.bin";
    states.verbose += 0x4;
    ftmp = fopen(states.tx_bits_file, "rb"); test_frame = fread(ftmp,Inf,"char")'; fclose(ftmp);
    states.ntestframebits = length(test_frame);
    printf("length test frame: %d\n", states.ntestframebits);
  end

  if test_frame_mode == 7
    % 800XA 4FSK modem --------------
    states = fsk_init(Fs=8000, Rs=400, M=4, P=10, nsym=256);
    states.tx_bits_file = "horus_high_speed.bin";
    states.verbose += 0x4;
    ftmp = fopen(states.tx_bits_file, "rb"); test_frame = fread(ftmp,Inf,"char")'; fclose(ftmp);
    states.ntestframebits = length(test_frame);
    printf("length test frame: %d\n", states.ntestframebits);
  end

  if test_frame_mode == 8
    % test RS41 type balllon telemetry --------------
    states = fsk_init(96000, 4800, 2, 10, 16);
    states.fest_fmin = 1000;
    states.fest_fmax = 40000;
    states.fest_min_spacing = 1000;
    states.tx_bits_file = "../build_linux/src/tx_bit.bin";
    states.verbose += 0x4;
    #ftmp = fopen(states.tx_bits_file, "rb"); test_frame = fread(ftmp,Inf,"char")'; fclose(ftmp);
    #states.ntestframebits = length(test_frame);
    #printf("length test frame: %d\n", states.ntestframebits);
    states.ntestframebits = 1000;
    read_complex = 1;
    shift_fs_on_4 = 1; % get samples into range of current freq estimator
  end

  if test_frame_mode == 9
    % Wenet high speed SSTV, we can just check raw demo here ---------------------
    % despite the high sample rate the modem sees this as a 8:1 Fs/Rs configuration
    states = fsk_init(8000, 1000, 2);
    states.tx_tone_separation = 1000;
    states.ntestframebits = (256+2+65)*8+40; % from src/drs232_lpc.c
    states.freq_est_type = 'mask';
    read_complex=1; sample_size = 'uint8'; 
    printf("Wenet mode: ntestframebits: %d freq_est_type: %s\n", states.ntestframebits, states.freq_est_type);
    %states.verbose = 0x8;
  end
                               
  N = states.N;
  P = states.P;
  Rs = states.Rs;
  nsym = states.nsym;
  nbit = states.nbit;

  frames = 0;
  rx = [];
  rx_bits_log = [];
  rx_bits_sd_log = [];
  norm_rx_timing_log = [];
  f_int_resample_log = [];
  EbNodB_log = [];
  ppm_log = [];
  f_log = []; Sf_log = [];
  
  rx_bits_buf = zeros(1,nbit + states.ntestframebits);

  % optional noise.  Useful for testing performance of waveforms from real world modulators
  % we need to pre-read the file to estimate the signal power
  ftmp = fopen(filename,"rb"); s = fread(ftmp,Inf,sample_size); fclose(ftmp);
  if sample_size == "uint8" s = (s - 127)/128; end
  if read_complex s = s(1:2:end) + j*s(2:2:end); end
  tx_pwr = var(s);
  EbNo = 10^(EbNodB/10);
  variance = (tx_pwr/2)*states.Fs/(states.Rs*EbNo*states.bitspersymbol);

  % First extract raw bits from samples ------------------------------------------------------

  printf("demod of raw bits....\n");

  finished = 0; ph = 1;
  while (finished == 0)

    % extract nin samples from input stream

    nin = states.nin;
    if read_complex
      [sf count] = fread(fin, 2*nin, sample_size);
      if sample_size == "uint8" sf = (sf - 127)/128; end
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
      [sf count] = fread(fin, nin, "short");
    end
    rx = [rx; sf];
    
    % add optional noise

    if count
      noise = sqrt(variance)*randn(count,1);
      sf += noise;
    end

    if count == nin
      frames++;

      % demodulate to stream of bits

      states = est_freq(states, sf, states.M);
      if states.freq_est_type == 'mask' states.f = states.f2; end
      [rx_bits states] = fsk_demod(states, sf);

      rx_bits_buf(1:states.ntestframebits) = rx_bits_buf(nbit+1:states.ntestframebits+nbit);
      rx_bits_buf(states.ntestframebits+1:states.ntestframebits+nbit) = rx_bits;

      rx_bits_log = [rx_bits_log rx_bits];
      rx_bits_sd_log = [rx_bits_sd_log states.rx_bits_sd];
      norm_rx_timing_log = [norm_rx_timing_log states.norm_rx_timing];
      f_int_resample_log = [f_int_resample_log abs(states.f_int_resample)];
      EbNodB_log = [EbNodB_log states.EbNodB];
      ppm_log = [ppm_log states.ppm];
      f_log = [f_log; states.f];
      Sf_log = [Sf_log; states.Sf'];
      
      if (test_frame_mode == 1)
        states = ber_counter(states, test_frame, rx_bits_buf);
        if states.ber_state == 1
          states.verbose = 0;
        end
      end
      if (test_frame_mode == 6)  % || (test_frame_mode == 8)
        states = ber_counter_packet(states, test_frame, rx_bits_buf);
      end
     else      
      finished = 1;
    end

    if frames > max_frames finished=1; end
      
  end
  printf("frames: %d\n", frames);
  fclose(fin);

  if noplot == 0
    printf("plotting...\n");

    figure(1); clf;
    plot(f_log);
    title('Tone Freq Estimates');
    
    figure(2);
    plot(f_int_resample_log','+')
    title('Integrator outputs for each tone');

    figure(3); clf
    subplot(211)
    plot(norm_rx_timing_log)
    axis([1 frames -0.5 0.5])
    title('norm fine timing')
    subplot(212)
    plot(states.nerr_log)
    title('num bit errors each frame')
 
    figure(4); clf
    plot(EbNodB_log);
    title('Eb/No estimate')

    figure(5); clf
    rx_nowave = rx(1000:length(rx)); % skip past wav header if it's a wave file
    subplot(211)
    plot(real(rx_nowave));
    title('input signal to demod (1 sec)')
    xlabel('Time (samples)');
    %axis([1 states.Fs -35000 35000])

    % normalise spectrum to 0dB full scale with sine wave input
    subplot(212);
    if sample_size == "int16" max_value = 32767; end
    if sample_size == "uint8" max_value = 127; end
    RxdBFS = 20*log10(abs(fft(rx_nowave(1:states.Fs)))) - 20*log10((states.Fs/2)*max_value);
    plot(RxdBFS)
    axis([1 states.Fs/2 -80 0])
    xlabel('Frequency (Hz)');

    figure(6); clf
    plot(ppm_log)
    title('Sample clock (baud rate) offset in PPM');

    figure(7); clf; mesh(Sf_log(1:10,:));
  end

  if (test_frame_mode == 1) || (test_frame_mode == 6)
    printf("frames: %d Tbits: %d Terrs: %d BER %4.3f EbNo: %3.2f\n", frames, states.Tbits,states. Terrs, states.Terrs/states.Tbits, mean(EbNodB_log));
  end

  % we can decode both protocols at the same time

  if (test_frame_mode == 4)
    npackets = extract_and_print_rtty_packets(states, rtty, rx_bits_log, rx_bits_sd_log)
    printf("Received %d packets\n", npackets);
  end
  
  if (test_frame_mode == 5)
    corr_log = extract_and_decode_binary_packets(states, binary, rx_bits_log);
    
    figure(8);
    clf
    plot(corr_log);
    hold on;
    plot([1 length(corr_log)],[binary.uw_thresh binary.uw_thresh],'g');
    hold off;
    title('UW correlation');
  end

endfunction


% Over the years this modem has been used for many different FSK signals ...

if exist("fsk_horus_as_a_lib") == 0
  run_sim(test_frame_mode=4, M=2, frames=30, EbNodB = 20);
  %run_sim(5, 4, 30, 100);
  %rx_bits = demod_file("~/Desktop/115.wav",6,0,90);
  %rx_bits = demod_file("~/Desktop/fsk_800xa_rx_hackrf.wav",7);
  %rx_bits = demod_file("~/Desktop/2fsk_100_rx_rpi_rtlsdr_002_ledger.wav",4);
  %rx_bits = demod_file("~/Desktop/phorus_binary_ascii.wav",4);
  %rx_bits = demod_file("~/Desktop/binary/horus_160102_binary_rtty_2.wav",4);
  %rx_bits = demod_file("~/Desktop/horus_160102_vk5ei_capture2.wav",4);
  %rx_bits = demod_file("~/Desktop/horus_rtty_binary.wav",4);
  %rx_bits = demod_file("~/Desktop/FSK_4FSK.wav",4);
  %rx_bits = demod_file("t.raw",5);
  %rx_bits = demod_file("~/Desktop/fsk_horus_10dB_1000ppm.wav",4);
  %rx_bits = demod_file("~/Desktop/fsk_horus_6dB_0ppm.wav",4);
  %rx_bits = demod_file("test.raw",1,1);
  %rx_bits = .rawdemod_file("/dev/ttyACM0",1);
  %rx_bits = demod_file("fsk_horus_rx_1200_96k.raw",1);
  %rx_bits = demod_file("mp.raw",4);
  %rx_bits = demod_file("~/Desktop/launchbox_v2_landing_8KHz_final.wav",4);
  %rx_bits = demod_file("~/Desktop/fsk_800xa.wav",7);
  %rx_bits = demod_file("~/Desktop/rs41_96k_10s.iq16",8);
end
