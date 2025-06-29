% fsk_lib_ldpc_rx.m
%
% LDPC coded 4FSK modem rx, reads 8 kHz 16 bit short raw file of real samples and demodulates

function fsk_lib_ldpc_rx(filename, Rs=100, coderate=0.5)
  fsk_lib_ldpc;
  
  % set up LDPC code
  init_cml();
  if coderate == 0.5
    load H_256_512_4.mat;
  elseif coderate == 0.75
    load HRAa_1536_512.mat; H=HRA;
  else
    disp("unknown code rate");
  end
  [states code_param] = fsk_lib_ldpc_init (H, Rs, Fs=8000);
  n = code_param.coded_bits_per_frame; k = code_param.data_bits_per_frame;

  % known transmitted bits for BER estimation
  rand('seed',1);
  data_bits = round(rand(1,code_param.data_bits_per_frame));
  codeword_bits = LdpcEncode(data_bits, code_param.H_rows, code_param.P_matrix);

  frx=fopen(filename,"rb"); rx = fread(frx, Inf, "short"); fclose(frx);

  % freq estimator and demod
  run_frames = floor(length(rx)/states.N)-1;
  st = 1; f_log = []; rx_bits = []; rx_filt = []; SNRest_log = []; rx_timing_log = [];
  for f=1:run_frames

    % extract nin samples from input stream
    nin = states.nin;
    en = st + states.nin - 1;

    % due to nin variations it's possible to overrun buffer
    if en < length(rx)
      sf = rx(st:en);
      states = est_freq(states, sf, states.M);  states.f = states.f2;
      [arx_bits states] = fsk_demod(states, sf);
      rx_bits = [rx_bits arx_bits];
      rx_filt = [rx_filt abs(states.f_int_resample)];
      f_log = [f_log; states.f];
      SNRest_log = [SNRest_log states.SNRest];
      rx_timing_log = [rx_timing_log states.norm_rx_timing];
      st += nin;
    end
  end

  % count bit errors in test frames
  
  num_frames=floor(length(rx_bits)/code_param.coded_bits_per_frame);
  log_nerrs = []; num_frames_rx = 0; Tbits = Terrs = Tperr = Tpackets = 0;
  uber = cber = 0.5; cper = 1;
  for f=1:num_frames-1
    st = (f-1)*code_param.coded_bits_per_frame + 1; en = (f+1)*code_param.coded_bits_per_frame;
    states = ber_counter(states, codeword_bits, rx_bits(st:en));
    log_nerrs = [log_nerrs states.nerr];
    if states.ber_state num_frames_rx++; end

    % Using sync provided by ber_counter() state machine for LDPC frame alignment
    if states.ber_state
      st_bit = (f-1)*code_param.coded_bits_per_frame + states.coarse_offset;
      st_symbol = (st_bit-1)/states.bitspersymbol + 1;
      en_symbol = st_symbol +  code_param.coded_bits_per_frame/states.bitspersymbol - 1;

      % map FSK filter ouputs to LLRs, then LDPC decode (see also fsk_cml_sam.m)
      symL = DemodFSK(1/states.v_est*rx_filt(:,st_symbol:en_symbol), states.SNRest, 1);     
      llr = -Somap(symL);
      [x_hat, PCcnt] = MpDecode(llr, code_param.H_rows, code_param.H_cols, max_iterations=100, decoder_type=0, 1, 1);
      Niters = sum(PCcnt~=0);
      detected_data = x_hat(Niters,:);
      Nerrs = sum(xor(data_bits, detected_data(1:code_param.data_bits_per_frame)));
      Terrs += Nerrs; 
      Tbits += code_param.data_bits_per_frame;
      if Nerrs Tperr++; end
      Tpackets++;
    end
  end

  SNRestdB_log = 10*log10(SNRest_log);
  if states.Tbits
    printf("Fs: %d Rs: %d rate %4.2f (%d,%d) frames received: %3d SNRav: %4.2f\n",
    Fs, Rs, coderate, n, k, num_frames_rx, mean(SNRestdB_log));
    uber = states.Terrs/states.Tbits; cber = Terrs/Tbits; cper = Tperr/Tpackets;
    printf("  Uncoded: nbits: %6d nerrs: %6d ber: %4.3f\n", states.Tbits, states.Terrs, uber);
    printf("  Coded..: nbits: %6d nerrs: %6d ber: %4.3f\n", Tbits, Terrs, cber);
    printf("  Coded..: npckt: %6d perrs: %6d per: %4.3f\n", Tpackets, Tperr, cper);
  else
    printf("No frames detected....\n");
  end

  figure(1); clf; subplot(211); plot(rx); axis([1 length(rx) -32767 32767]); subplot(212); plot_specgram(rx);
  figure(2); clf;
  subplot(211); plot(f_log); axis([1 length(f_log) states.fest_fmin states.fest_fmax]); ylabel('Tone Freq (Hz)');
  subplot(212); plot(rx_timing_log); axis([1 length(rx_timing_log) -0.5 0.5]); ylabel('Timing');
  figure(3); clf;
  mx_SNRestdB = 5*ceil(max(SNRestdB_log)/5);
  subplot(211); plot(SNRestdB_log); axis([1 length(SNRestdB_log) 0 mx_SNRestdB]); ylabel('SNRest (dB)');
  subplot(212); stem(log_nerrs); ylabel('Uncoded errors');
end

