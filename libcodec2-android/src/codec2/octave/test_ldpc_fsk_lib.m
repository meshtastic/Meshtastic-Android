% test_ldpc_fsk_lib
% David Rowe 16 April 2016
%
% A series of tests for ldpc_fsk_lib, and C versions ldpc_enc and ldpc_dec.
% Gradually builds up complete C command line for SSTV balloon system,
% using Octave versions of LDPC and FSK modem as reference points.

1;

% encodes and decodes one frame, also writes codeword.bin for testing
% decode_from_file() below, and can optionally generate include file for
% C version of decoder.

function [data code_param] = simple_ut(c_include_file)
  load('H2064_516_sparse.mat');
  HRA = full(HRA);  
  max_iterations = 100;
  decoder_type = 0;
  EsNodB = 3;
  mod_order = 2;

  code_param = ldpc_init(HRA, mod_order);
  data = round( rand( 1, code_param.data_bits_per_frame ) );
  codeword = ldpc_encode(code_param, data);
  f = fopen("codeword.bin","wt"); fwrite(f, codeword, "uint8"); fclose(f);
  s = 1 - 2 * codeword;   
  code_param.symbols_per_frame = length( s );

  EsNo = 10^(EsNodB/10);
  variance = 1/(2*EsNo);
  noise = sqrt(variance)* randn(1,code_param.symbols_per_frame); 
  rx = s + noise;
  
  if nargin == 1
    code_param.c_include_file = c_include_file;
  end
  [detected_data Niters] = ldpc_decode(rx, code_param, max_iterations, decoder_type);
  
  error_positions = xor(detected_data(1:code_param.data_bits_per_frame), data);
  Nerrs = sum(error_positions);

  printf("Nerrs = %d\n", Nerrs);
end


% This version decodes from a file of bits

function detected_data = decode_from_file(filename)
  max_iterations = 100;
  decoder_type = 0;
  load('H2064_516_sparse.mat');
  HRA = full(HRA);  
  mod_order = 2;

  f = fopen(filename,"rb"); codeword = fread(f, "uint8")'; fclose(f);
  r = 1 - 2 * codeword;   
  code_param = ldpc_init(HRA, mod_order);
  [detected_data Niters] = ldpc_decode(r, code_param, max_iterations, decoder_type);
end


% plots a BER curve for the LDPC decoder.  Takes a while to run, uses parallel cores

function plot_curve
  num_cores = 4;              % set this to the number of cores you have

  load('H2064_516_sparse.mat');
  HRA = full(HRA);  
  [Nr Nc] = size(HRA); 
  sim_in.rate = (Nc-Nr)/Nc;

  sim_in.HRA            = HRA;
  sim_in.mod_order      = 2;
  sim_in.framesize      = Nc;
  sim_in.mod_order      = 2; 
  sim_in.Lim_Ferrs      = 100;

  % note we increase number of trials as BER goes down

  Esvec   = [   0 0.5 1.0 1.5 2.0 2.5 3.0 3.5 4.0 ]; 
  Ntrials = [ 1E4 1E4 1E4 1E4 1E5 1E5 1E5 1E5 1E5 ];
  num_runs = length(Esvec)

  sim_in_vec(1:num_runs) = sim_in;
  for i = 1:num_runs
    sim_in_vec(i).Esvec   = Esvec(i);
    sim_in_vec(i).Ntrials = Ntrials(i);
  end

  %sim_out = ldpc5(sim_in_vec(1));
  tstart = time();
  sim_out = pararrayfun(num_cores, @ldpc5, sim_in_vec);
  tend = time();

  total_bits = sum(Ntrials)*sim_in.framesize;
  total_secs = tend - tstart;
  printf("%d bits in %4.1f secs, or %5f bits/s\n", total_bits, total_secs, total_bits/total_secs);

  for i=1:num_runs
    Ebvec(i)  = sim_out(i).Ebvec;
    BERvec(i) = sim_out(i).BERvec;
  end
  semilogy(Ebvec,  BERvec, '+-')
  xlabel('Eb/N0')
  ylabel('BER')
  title(['H2064 516 sparse.mat' ' ' date])

end


% Test C encoder

function test_c_encoder
  load('H2064_516_sparse.mat');
  HRA = full(HRA);  
  max_iterations = 100;
  decoder_type = 0;
  EsNodB = 3;
  mod_order = 2;
  frames = 100;

  EsNo = 10^(EsNodB/10);
  variance = 1/(2*EsNo);

  code_param = ldpc_init(HRA, mod_order);

  data = round(rand(1,frames*code_param.data_bits_per_frame));
  f = fopen("data.bin","wt"); fwrite(f, data, "uint8"); fclose(f);

  % Outboard C encoder

  system("../src/ldpc_enc data.bin codewords.bin"); 

  % Test with Octave decoder

  f = fopen("codewords.bin","rb"); codewords = fread(f, "uint8")'; fclose(f);
  
  Nerrs = 0;
  for i=1:frames
    st = (i-1)*code_param.symbols_per_frame+1; en = st+code_param.symbols_per_frame-1;
    tx = 1 - 2 * codewords(st:en);   

    noise = sqrt(variance)*randn(1,code_param.symbols_per_frame); 
    rx = tx + noise;

    [detected_data Niters] = ldpc_decode(rx, code_param, max_iterations, decoder_type);

    st = (i-1)*code_param.data_bits_per_frame+1; en = st+code_param.data_bits_per_frame-1;
    error_positions = xor(detected_data(1:code_param.data_bits_per_frame), data(st:en));
    Nerrs += sum(error_positions);
  end

  printf("Nerrs = %d\n", Nerrs);
end


function test_c_decoder
  load('H2064_516_sparse.mat');
  HRA = full(HRA);  
  max_iterations = 100;
  decoder_type = 0;
  mod_order = 2;
  frames = 10;
  EsNodB = 2;
  sdinput = 1;

  EsNo = 10^(EsNodB/10);
  variance = 1/(2*EsNo);

  code_param = ldpc_init(HRA, mod_order);
  data = round(rand(1,code_param.data_bits_per_frame*frames));
  
  f = fopen("data.bin","wt"); fwrite(f, data, "uint8"); fclose(f);
  system("../src/ldpc_enc data.bin codewords.bin"); 
  f = fopen("codewords.bin","rb"); codewords = fread(f, "uint8")'; fclose(f);

  s = 1 - 2 * codewords;   
  noise = sqrt(variance)*randn(1,code_param.symbols_per_frame*frames); 
  r = s + noise;

  % calc LLRs frame by frame

  for i=1:frames
    st = (i-1)*code_param.symbols_per_frame+1;
    en = st + code_param.symbols_per_frame-1;
    llr(st:en) = sd_to_llr(r(st:en));    
  end

  % Outboard C decoder

  if sdinput
    f = fopen("sd.bin","wb"); fwrite(f, r, "double"); fclose(f);
    system("../src/ldpc_dec sd.bin data_out.bin --sd"); 
  else
    f = fopen("llr.bin","wb"); fwrite(f, llr, "double"); fclose(f);
    system("../src/ldpc_dec llr.bin data_out.bin"); 
  end

  f = fopen("data_out.bin","rb"); data_out = fread(f, "uint8")'; fclose(f);
  
  Nerrs = Nerrs2 = zeros(1,frames);
  for i=1:frames

    % Check C decoder
    
    data_st = (i-1)*code_param.data_bits_per_frame+1;
    data_en = data_st+code_param.data_bits_per_frame-1;
    st = (i-1)*code_param.symbols_per_frame+1;
    en = st+code_param.data_bits_per_frame-1;
    data_out_c = data_out(st:en);
    error_positions = xor(data_out_c, data(data_st:data_en));
    Nerrs(i) = sum(error_positions);

    % Octave decoder 

    st = (i-1)*code_param.symbols_per_frame+1; en = st+code_param.symbols_per_frame-1;
    [detected_data Niters] = ldpc_decode(r(st:en), code_param, max_iterations, decoder_type);
    st = (i-1)*code_param.data_bits_per_frame+1; en = st+code_param.data_bits_per_frame-1;
    data_out_octave = detected_data(1:code_param.data_bits_per_frame);
    error_positions = xor(data_out_octave, data(st:en));
    Nerrs2(i) = sum(error_positions);
    %printf("%4d ", Niters);
  end
  printf("Errors per frame:\nC.....:");
  for i=1:frames
    printf("%4d ", Nerrs(i));
  end
  printf("\nOctave:");
  for i=1:frames
    printf("%4d ", Nerrs2(i));
  end
  printf("\n");

end

% Saves a complex vector s to a file "filename" of IQ unsigned 8 bit
% chars, same as RTLSDR format.

function save_rtlsdr(filename, s)
  mx = max(abs(s));
  re = real(s); im = imag(s);
  l = length(s);
  iq = zeros(1,2*l);
  %iq(1:2:2*l) = 127 + re*(127/mx); 
  %iq(2:2:2*l) = 127 + im*(127/mx); 
  iq(1:2:2*l) = 127 + 32*re; 
  iq(2:2:2*l) = 127 + 32*im; 
  figure(3); clf; plot(iq); title('simulated IQ signal from RTL SDR');
  fs = fopen(filename,"wb");
  fwrite(fs,iq,"uchar");
  fclose(fs);
endfunction


% Oversamples by a factor of 2 using Octaves resample() function then
% uses linear interpolation to achive fractional sample rate

function rx_resample_fract = fractional_resample(rx, resample_rate);
    assert(resample_rate < 2, "keep resample_rate between 0 and 2");
    rx_resample2 = resample(rx, 2, 1);
    l = length(rx_resample2);
    rx_resample_fract = zeros(1,l);
    k = 1;
    step = 2/resample_rate;
    for i=1:step:l-1
      i_low = floor(i);
      i_high = ceil(i);
      f = i - i_low;
      rx_resample_fract(k) = (1-f)*rx_resample2(i_low) + f*rx_resample2(i_high); 
      %printf("i: %f i_low: %d i_high: %d f: %f\n", i, i_low, i_high, f);
      k++;
    end
    rx_resample_fract = rx_resample_fract(1:k-1);
endfunction


% Using simulated SSTV packet, generate complex fsk mod signals, 8-bit
% unsigned IQ for feeding into C demod chain.  Can also be used to
% generate BER curves.  Found bugs in UW size and our use of csdr
% re-sampler using this function, and by gradually and carefully
% building up the C command line.

#{
todo: [X] uncoded BER
          [X] octave fsk demod
          [X] use C demod
      [X] compare uncoded BER to unsigned 8 bit IQ to regular 16-bit
          [X] generate complex rx signal with noise
          [X] used cmd line utils to drive demod
      [X] test with resampler
      [X] measure effect on PER with coding
#}

function [n_uncoded_errs n_uncoded_bits] = run_sstv_sim(sim_in, EbNodB)

  frames = sim_in.frames;
  demod_type = sim_in.demod_type;

  % init LDPC code

  load('H2064_516_sparse.mat');
  HRA = full(HRA);  
  max_iterations = 100;
  decoder_type = 0;
  mod_order = 2;

  code_param = ldpc_init(HRA, mod_order);

  % note fixed frame of bits used for BER testing

  tx_codeword = gen_sstv_frame;
  
  % init FSK modem

  fsk_horus_as_a_lib = 1;
  fsk_horus;
  states         = fsk_horus_init_hbr(9600, 8, 1200, 2, length(tx_codeword));
  states.df(1:states.M) = 0;
  states.dA(1:states.M) = 1;
  states.tx_real = 0;  % Octave fsk_mod generates complex valued output
                       % so we can simulate rtl_sdr complex ouput

  % Set up simulated tx tones to sit in the middle of cdsr passband

  filt_low_norm = 0.1; filt_high_norm = 0.4;
  fc = states.Fs*(filt_low_norm + filt_high_norm)/2;
  %fc = 1800;
  f1 = fc - states.Rs/2;
  f2 = fc + states.Rs/2;
  states.ftx = [f1 f2];

  % set up AWGN channel 

  EbNo = 10^(EbNodB/10);
  variance = states.Fs/(states.Rs*EbNo*states.bitspersymbol);

  % start simulation ----------------------------------------

  tx_bit_stream = [];
  for i=1:frames
    % uncomment for different data on each frame
    %tx_codeword = gen_sstv_frame;
    tx_bit_stream = [tx_bit_stream tx_codeword];
  end

  printf("%d bits at %d bit/s is a %3.1f second run\n", length(tx_bit_stream), 115200,length(tx_bit_stream)/115200);
 
  % modulate and channel model

  tx = fsk_horus_mod(states, tx_bit_stream);
  noise_real = sqrt(variance)*randn(length(tx),1);
  noise_complex = sqrt(variance/2)*(randn(length(tx),1) + j*randn(length(tx),1));

  % demodulate -----------------------------------------------------

  if demod_type == 1

    % Octave demod

    if states.tx_real
      rx = tx + noise_real;
    else
      rx = tx + noise_complex;
    end
    SNRdB = 10*log10(var(tx)/var(noise_complex));

    % demodulate frame by frame using Octave demod

    st = 1;
    run_frames = floor(length(rx)/states.N);
    rx_bit_stream = [];
    rx_sd_stream = [];
    for f=1:run_frames

      % extract nin samples from rx sample stream

      nin = states.nin;
      en = st + states.nin - 1;

      if en <= length(rx) % due to nin variations its possible to overrun buffer
        sf = rx(st:en);
        st += nin;

        % demodulate to stream of bits

        states.f = [f1 f2]; % note that for Octave demod we cheat and use known tone frequencies
                            % allows us to determine if freq offset estimation in C demod is a problem

        [rx_bits states] = fsk_horus_demod(states, sf);
        rx_bit_stream = [rx_bit_stream rx_bits];
        rx_sd_stream = [rx_sd_stream states.rx_bits_sd];
      end
    end
  end

  if demod_type == 2
    % baseline C demod

    if states.tx_real
      rx = tx + noise_real;
    else
      rx = 2*real(tx) + noise_real;
    end
    SNRdB = 10*log10(var(tx)/var(noise_real));
    rx_scaled = 1000*real(rx);
    f = fopen("fsk_demod.raw","wb"); fwrite(f, rx_scaled, "short"); fclose(f);
    system("../build_linux/src/fsk_demod 2X 8 9600 1200 fsk_demod.raw fsk_demod.bin");
    f = fopen("fsk_demod.bin","rb"); rx_bit_stream = fread(f, "uint8")'; fclose(f);
  end

  if demod_type == 3
    % C demod driven by csdr command line kung fu

    assert(states.tx_real == 0, "need complex signal for this test");
    rx = tx + noise_complex;
    SNRdB = 10*log10(var(tx)/var(noise_real));
    save_rtlsdr("fsk_demod.iq", rx);
    system("cat fsk_demod.iq | csdr convert_u8_f | csdr bandpass_fir_fft_cc 0.1 0.4 0.05 | csdr realpart_cf | csdr convert_f_s16 | ../build_linux/src/fsk_demod 2X 8 9600 1200 - fsk_demod.bin");
    f = fopen("fsk_demod.bin","rb"); rx_bit_stream = fread(f, "uint8")'; fclose(f);
  end

  if demod_type == 4
    % C demod with resampler ....... getting closer to Mark's real time cmd line

    assert(states.tx_real == 0, "need complex signal for this test");
    rx = tx + noise_complex;
    SNRdB = 10*log10(var(tx)/var(noise_real));
    
    printf("resampling ...\n");
    rx_resample_fract = fractional_resample(rx, 1.08331);
    %rx_resample_fract = fractional_resample(rx_resample_fract, 1/1.08331);
    save_rtlsdr("fsk_demod_resample.iq", rx_resample_fract);

    printf("run C cmd line chain ...\n");
%    system("cat fsk_demod_resample.iq | csdr convert_u8_f | csdr bandpass_fir_fft_cc 0.1 0.4 0.05 | csdr realpart_cf | csdr convert_f_s16 | ../build_linux/src/fsk_demod 2X 8 9600 1200 - fsk_demod.bin");
    system("cat fsk_demod_resample.iq | csdr convert_u8_f | csdr bandpass_fir_fft_cc 0.1 0.4 0.05 | csdr realpart_cf | csdr convert_f_s16 | ../unittest/tsrc - - 0.9230968 | ../build_linux/src/fsk_demod 2X 8 9600 1200 - fsk_demod.bin");
%    system("cat fsk_demod_resample.iq | csdr convert_u8_f | csdr bandpass_fir_fft_cc 0.1 0.4 0.05 | csdr realpart_cf | csdr fractional_decimator_ff 1.08331 | csdr convert_f_s16 | ../build_linux/src/fsk_demod 2X 8 9600 1200 - fsk_demod.bin");
    f = fopen("fsk_demod.bin","rb"); rx_bit_stream = fread(f, "uint8")'; fclose(f);
  end


  if demod_type == 5

    % C demod with resampler and use C code to measure PER, in this
    % test we don't need to run state machine below as C code gives us
    % the ouputs we need

    assert(states.tx_real == 0, "need complex signal for this test");
    rx = tx + noise_complex;
    SNRdB = 10*log10(var(tx)/var(noise_real));
    
    printf("fract resampling ...\n");
    rx_resample_fract = fractional_resample(rx, 1.08331);
    save_rtlsdr("fsk_demod_resample.iq", rx_resample_fract);

    % useful for HackRF
    %printf("10X resampling ...\n");
    %rx_resample_10M = resample(rx_resample_fract, 10, 1);
    %save_rtlsdr("fsk_demod_10M.iq", rx_resample_10M);

    printf("run C cmd line chain - uncoded PER\n");
    system("cat fsk_demod_resample.iq | csdr convert_u8_f | csdr bandpass_fir_fft_cc 0.1 0.4 0.05 | csdr realpart_cf | csdr convert_f_s16 | ../unittest/tsrc - - 0.9230968 | ../build_linux/src/fsk_demod 2X 8 9600 1200 - - | ../src/drs232 - /dev/null -v");

    printf("run C cmd line chain - LDPC coded PER\n");
    system("cat fsk_demod_resample.iq | csdr convert_u8_f | csdr bandpass_fir_fft_cc 0.1 0.4 0.05 | csdr realpart_cf | csdr convert_f_s16 | ../unittest/tsrc - - 0.9230968 | ../build_linux/src/fsk_demod 2XS 8 9600 1200 - - | ../src/drs232_ldpc - /dev/null -v");
  end

  if demod_type == 6
    % C demod with complex input driven simplfied csdr command line, just measure BER of demod

    assert(states.tx_real == 0, "need complex signal for this test");
    rx = tx + noise_complex;
    SNRdB = 10*log10(var(tx)/var(noise_real));
    save_rtlsdr("fsk_demod.iq", rx);
    system("cat fsk_demod.iq | csdr convert_u8_f | csdr convert_f_s16 | ../build_linux/src/fsk_demod 2X 8 9600 1200 - fsk_demod.bin C");

    f = fopen("fsk_demod.bin","rb"); rx_bit_stream = fread(f, "uint8")'; fclose(f);
  end

  if demod_type == 7
    % C demod with complex input, measure uncoded and uncoded PER

    assert(states.tx_real == 0, "need complex signal for this test");
    rx = tx + noise_complex;
    SNRdB = 10*log10(var(tx)/var(noise_real));
    save_rtlsdr("fsk_demod.iq", rx);

    printf("run C cmd line chain - uncoded PER\n");
    system("cat fsk_demod.iq | csdr convert_u8_f | csdr convert_f_s16 | ../build_linux/src/fsk_demod 2X 8 9600 1200 - - C | ../src/drs232 - /dev/null -v");

    printf("run C cmd line chain - LDPC coded PER\n");
    %system("cat fsk_demod.iq | csdr convert_u8_f | csdr convert_f_s16 | ../build_linux/src/fsk_demod 2XS 8 9600 1200 - - C | ../src/drs232_ldpc - /dev/null -v");
    system("cat fsk_demod.iq | ../build_linux/src/fsk_demod 2XS 8 9600 1200 - - CU8 | ../src/drs232_ldpc - /dev/null -v");
  end

  if (demod_type != 5) && (demod_type != 7)
    % state machine. Look for SSTV UW.  When found count bit errors over one frame of bits

    state = "wait for uw";
    start_uw_ind = 16*10+1; end_uw_ind = start_uw_ind + 5*10 - 1;
    uw_rs232 = tx_codeword(start_uw_ind:end_uw_ind); luw = length(uw_rs232);
    start_frame_ind =  end_uw_ind + 1;
    nbits = length(rx_bit_stream);
    uw_thresh = 5;
    n_uncoded_errs = 0;
    n_uncoded_bits = 0;
    n_packets_rx = 0;
    last_i = 0;

    % might as well include RS232 framing bits in uncoded error count

    nbits_frame = code_param.data_bits_per_frame*10/8;  

    uw_errs = zeros(1, nbits);
    for i=luw:nbits
      uw_errs(i) = sum(xor(rx_bit_stream(i-luw+1:i), uw_rs232));
    end

    for i=luw:nbits
      next_state = state;
      if strcmp(state, 'wait for uw')
        if uw_errs(i) <= uw_thresh
          next_state = 'count errors';
          tx_frame_ind = start_frame_ind;
          rx_frame_ind = i + 1;
          n_uncoded_errs_this_frame = 0;
          %printf("%d %s %s\n", i, state, next_state);
          if last_i
            printf("i: %d i-last_i: %d ", i, i-last_i);
          end
        end
      end
      if strcmp(state, 'count errors')
        n_uncoded_errs_this_frame += xor(rx_bit_stream(i), tx_codeword(tx_frame_ind));
        n_uncoded_bits++;
        tx_frame_ind++;
        if tx_frame_ind == (start_frame_ind+nbits_frame)
          n_uncoded_errs += n_uncoded_errs_this_frame;
          printf("n_uncoded_errs_this_frame: %d\n", n_uncoded_errs_this_frame);
          frame_rx232_rx = rx_bit_stream(rx_frame_ind:rx_frame_ind+nbits_frame-1);
          %tx_codeword(start_frame_ind+1:start_frame_ind+10)
          %frame_rx232_rx(1:10)
          sstv_checksum(frame_rx232_rx);
          last_i = i;
          n_packets_rx++;
          next_state = 'wait for uw';
        end
      end
      state = next_state;
    end

    uncoded_ber = n_uncoded_errs/n_uncoded_bits;
    printf("EbNodB: %4.1f SNRdB: %4.1f pkts: %d bits: %d errs: %d BER: %4.3f\n", 
            EbNodB, SNRdB, n_packets_rx, n_uncoded_bits, n_uncoded_errs, uncoded_ber);  

    figure(2);
    plot(uw_errs);
    title('Unique Word Hamming Distance')
  end

endfunction


% Function to test flight mode software.  Takes a rx stream of
% demodulated bits, and locates frames using UW detection.  Extracts
% data and parity bits.  Uses data bits to generate parity bits here
% and compare.

function compare_parity_bits(rx_bit_stream)
    nframes = 500;

    % init LDPC code

    load('H2064_516_sparse.mat');
    HRA = full(HRA);  
    max_iterations = 100;
    decoder_type = 0;
    mod_order = 2;

    code_param = ldpc_init(HRA, mod_order);

    % generate frame, this will have random bits not related to
    % rx_stream, however we just use it for the UW

    tx_codeword = gen_sstv_frame;
    l = length(tx_codeword);
    printf("expected rs232 frames codeword length: %d\n", l);

    % state machine. Look for SSTV UW.  When found count bit errors over one frame of bits

    state = "wait for uw";
    start_uw_ind = 16*10+1; end_uw_ind = start_uw_ind + 4*10 - 1;
    uw_rs232 = tx_codeword(start_uw_ind:end_uw_ind); luw = length(uw_rs232);
    start_frame_ind =  end_uw_ind + 1;
    nbits = nframes*l;
    uw_thresh = 5;
    n_uncoded_errs = 0;
    n_uncoded_bits = 0;
    n_packets_rx = 0;
    last_i = 0;

    % might as well include RS232 framing bits in uncoded error count

    uw_errs = luw*ones(1, nbits);
    for i=luw:nbits
      uw_errs(i) = sum(xor(rx_bit_stream(i-luw+1:i), uw_rs232));
    end

    frame_start = find(uw_errs < 2)+1;
    nframes = length(frame_start)
    for i=1:nframes

      % double check UW OK

      st_uw = frame_start(i) - luw; en_uw = frame_start(i) - 1;
      uw_err_check = sum(xor(rx_bit_stream(st_uw:en_uw), uw_rs232));
      %printf("uw_err_check: %d\n", uw_err_check);

      % strip off rs232 start/stop bits

      nbits_rs232 = (256+2+65)*10;
      nbits = (256+2+65)*8;
      nbits_byte = 10;
      rx_codeword = zeros(1,nbits);
      pdb = 1;

      for k=1:nbits_byte:nbits_rs232
        for l=1:8
          rx_codeword(pdb) = rx_bit_stream(frame_start(i)-1+k+l);
          pdb++;
        end
      end
      assert(pdb == (nbits+1));
      
      data_bits = rx_codeword(1:256*8);
      checksum_bits = rx_codeword(256*8+1:258*8);
      parity_bits = rx_codeword(258*8+1:258*8+516);
      padding_bits = rx_codeword(258*8+516+1:258*8+516+1);

      % stopped here as we found bug lol!
    end

    figure(1); clf;
    plot(uw_errs);
    title('Unique Word Hamming Distance')
    figure(2); clf;
    lframe_start = length(frame_start);
    plot(frame_start(2:lframe_start)-frame_start(1:lframe_start-1));
    %title('Unique Word Hamming Distance')

endfunction


% Start simulation --------------------------------------------------------

more off;
currentdir = pwd;
thiscomp = computer;

if strcmpi(thiscomp, 'MACI64')==1
   if exist('CMLSimulate')==0
        cd '/Users/bill/Current/Projects/DLR_FSO/Visit2013_FSO_GEO/cml'
        addpath '../'    % assume the source files stored here
        CmlStartup       % note that this is not in the cml path!
        disp('added MACI64 path and run CmlStartup')
    end
end

if strfind(thiscomp, 'pc-linux-gnu')==8 
   if exist('LdpcEncode')==0, 
        cd '~/tmp/cml'
        CmlStartup 
        disp('CmlStartup has been run')
	% rmpath '/home/bill/cml/mexhelp'  % why is this needed? 
	% maybe different path order in octave cf matlab ? 
    end
end

cd(currentdir)

ldpc_fsk_lib;
randn('state',1);
rand('state',1);

% ------------------ select which demo/test to run here ---------------

demo = 12;

if demo == 1
  printf("simple_ut....\n");
  data = simple_ut;
end

if demo == 2
  printf("generate C header file....\n");
  data = simple_ut("../src/H2064_516_sparse.h");
end

if demo == 3
  printf("decode_from_file ......\n");
  data = simple_ut;
  detected_data = decode_from_file("codeword.bin");
  error_positions = xor( detected_data(1:length(data)), data );
  Nerrs = sum(error_positions);
  printf("  Nerrs = %d\n", Nerrs);
end

if demo == 4
  printf("plot a curve....\n");
  plot_curve;
end

if demo == 5

  % generate test data and save to disk

  [data code_param] = simple_ut;
  f = fopen("dat_in2064.bin","wb"); fwrite(f, data, "uint8"); fclose(f);

  % Outboard C encoder

  system("../src/ldpc_enc dat_in2064.bin dat_op2064.bin"); 

  % Test with Octave decoder

  detected_data = decode_from_file("dat_op2064.bin");
  error_positions = xor(detected_data(1:length(data)), data);
  Nerrs = sum(error_positions);
  printf("Nerrs = %d\n", Nerrs);
end

if demo == 6
  test_c_encoder;
end

if demo == 7
  test_c_decoder;
end

% generates simulated demod soft decision symbols to drive C ldpc decoder with

if demo == 8
  frames = 100;
  EsNodB = 3;
  EsNo = 10^(EsNodB/10);
  variance = 1/(2*EsNo);

  frame_rs232 = [];
  for i=1:frames
    frame_rs232 = [frame_rs232 gen_sstv_frame];
  end

  % write hard decn version to disk file, useful for fsk_mod input

  f = fopen("sstv.bin","wb"); fwrite(f, frame_rs232, "char"); fclose(f);

  % soft decision version (with noise)

  s = 1 - 2*frame_rs232;
  noise = sqrt(variance)*randn(1,length(frame_rs232)); 
  r = s + noise;
  f = fopen("sstv_sd.bin","wb"); fwrite(f, r, "float32"); fclose(f);
end


if demo == 9
  frames = 100;
  EbNodB = 11;

  frame_rs232 = [];
  for i=1:frames
    frame_rs232 = [frame_rs232 gen_sstv_frame];
  end

  % Use C FSK modulator to generate modulated signal

  f = fopen("sstv.bin","wb"); fwrite(f, frame_rs232, "char"); fclose(f);
  system("../build_linux/src/fsk_mod 2 9600 1200 1200 2400 sstv.bin fsk_mod.raw");

  % Add some channel noise here in Octave

  f = fopen("fsk_mod.raw","rb"); tx = fread(f, "short")'; fclose(f); tx_pwr = var(tx);
  Fs = 9600; Rs=1200; EbNolin = 10 ^ (EbNodB/10);
  variance = (tx_pwr/2)*states.Fs/(states.Rs*EbNolin*states.bitspersymbol);
  noise = sqrt(variance)*randn(1,length(tx)); 
  SNRdB = 10*log10(var(tx)/var(noise));
  rx = tx + noise;
  f = fopen("fsk_demod.raw","wb"); tx = fwrite(f, rx, "short"); fclose(f);
 
  % Demodulate using C modem and C de-framer/LDPC decoder

  system("../build_linux/src/fsk_demod 2XS 8 9600 1200 fsk_demod.raw - | ../src/drs232_ldpc - dummy_out.bin");
end


% Plots uncoded BER curves for two different SSTV simulations.  Used
% to compare results with different processing steps as we build up C
% command line.  BER curves are powerful ways to confirm system is
% operating as expected, several bugs were found using this system.

if demo == 10
  sim_in.frames = 10;
  EbNodBvec = 7:10;

  sim_in.demod_type = 3;
  ber_test1 = [];
  for i = 1:length(EbNodBvec)
    [n_uncoded_errs n_uncoded_bits] = run_sstv_sim(sim_in, EbNodBvec(i));
    ber_test1(i) = n_uncoded_errs/n_uncoded_bits;
  end
  
  sim_in.demod_type = 4;
  ber_c = [];
  for i = 1:length(EbNodBvec)
    [n_uncoded_errs n_uncoded_bits] = run_sstv_sim(sim_in, EbNodBvec(i));
    ber_test2(i) = n_uncoded_errs/n_uncoded_bits;
  end

  figure(1);
  clf;
  semilogy(EbNodBvec,  ber_test1, '+-;first test;')
  grid;
  xlabel('Eb/No (dB)')
  ylabel('BER')

  hold on;
  semilogy(EbNodBvec,  ber_test2, 'g+-;second test;')
  legend("boxoff");
  hold off;
  
end

% Measure PER of complete coded and uncoded system

if demo == 11
  sim_in.frames = 10;
  EbNodB = 9;
  sim_in.demod_type = 7;
  run_sstv_sim(sim_in, EbNodB);
end


% Compare parity bits from an off-air stream of demodulated bits
% Use something like:
%   cat ~/Desktop/923096fs_wenet.iq | ../build_linux/src/fsk_demod 2X 8 9600 1200 - fsk_demod.bin CU8
% (note not soft dec mode)
if demo == 12
  f = fopen("fsk_demod.bin","rb"); rx_bit_stream = fread(f, "uint8")'; fclose(f);

  compare_parity_bits(rx_bit_stream);
end
