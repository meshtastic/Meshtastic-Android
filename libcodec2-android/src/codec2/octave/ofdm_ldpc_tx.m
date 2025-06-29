% ofdm_ldpc_tx.m
% David Rowe April 2017
%
% File based ofdm tx with LDPC encoding and interleaver.  Generates a
% file of ofdm samples, including optional channel simulation.

#{ 
  1. 10 seconds, AWGN channel at SNR3k=3dB

    octave:4> ofdm_ldpc_tx("test_700d.raw", "700D", 10, 3)

  2. 10 seconds, multipath poor channel at SNR=6dB

    octave:5> ofdm_ldpc_tx("test_700d.raw", "700D", 10, 6, "mpp")
    
  3. Data mode example, three bursts of one packet each, SNR=100dB:
  
    octave:6> ofdm_ldpc_tx("test_datac0.raw","datac0",1,100,"awgn","bursts",3)
    
#}

function ofdm_ldpc_tx(filename, mode="700D", N, SNR3kdB=100, channel='awgn', varargin)
  ofdm_lib;
  ldpc;
  gp_interleaver;
  channel_lib;
  pkg load signal;
  randn('seed',1);
  more off;

  tx_clip_en = 0; freq_offset_Hz = 0.0; burst_mode = 0; Nbursts = 1;
  i = 1;
  while i<=length(varargin)
    if strcmp(varargin{i},"txclip") 
      txclip_en = 1;
    elseif strcmp(varargin{i},"bursts") 
      burst_mode = 1;
      Nbursts = varargin{i+1}; i++;
    else
      printf("\nERROR unknown argument: %s\n", varargin{i});
      return;
    end
    i++;      
  end
 
  % init modem

  config = ofdm_init_mode(mode);
  states = ofdm_init(config);
  print_config(states);
  ofdm_load_const;

  if burst_mode
    % burst mode: treat N as Npackets
    Npackets = N; 
  else
    % streaming mode: treat N as Nseconds
    Npackets = round(N/states.Tpacket);
  end

  % some constants used for assembling modem frames
  [code_param Nbitspercodecframe Ncodecframespermodemframe] = codec_to_frame_packing(states, mode);

  % OK generate a modem frame using random payload bits

  if strcmp(mode, "2020")
    payload_bits = round(ofdm_rand(Ncodecframespermodemframe*Nbitspercodecframe)/32767);
  else
    payload_bits = round(ofdm_rand(code_param.data_bits_per_frame)/32767);
  end
  [packet_bits bits_per_packet] = fec_encode(states, code_param, mode, payload_bits, Ncodecframespermodemframe, Nbitspercodecframe);

  % modulate to create symbols and interleave
  tx_symbols = [];
  for b=1:bps:bits_per_packet
    if bps == 2 tx_symbols = [tx_symbols qpsk_mod(packet_bits(b:b+bps-1))]; end
    if bps == 4 tx_symbols = [tx_symbols qam16_mod(states.qam16, packet_bits(b:b+bps-1))]; end
  end
  assert(gp_deinterleave(gp_interleave(tx_symbols)) == tx_symbols);
  tx_symbols = gp_interleave(tx_symbols);

  % generate txt (non FEC protected) symbols
  txt_bits = zeros(1,Ntxtbits);
  txt_symbols = [];
  for b=1:bps:length(txt_bits)
    if bps == 2 txt_symbols = [txt_symbols qpsk_mod(txt_bits(b:b+bps-1))]; end
    if bps == 4 txt_symbols = [txt_symbols qam16_mod(states.qam16,txt_bits(b:b+bps-1))]; end
  end

  % assemble interleaved modem packet that include UW and txt symbols
  modem_packet = assemble_modem_packet_symbols(states, tx_symbols, txt_symbols);

  % sanity check
  [rx_uw rx_codeword_syms payload_amps txt_bits] = disassemble_modem_packet(states, modem_packet, ones(1,length(modem_packet)));
  assert(rx_uw == states.tx_uw);

  % create a burst of concatenated packets
  atx = ofdm_txframe(states, modem_packet); tx = [];
  for f=1:Npackets
    tx = [tx atx];
  end
  if length(states.data_mode)
    % note for burst mode postamble provides a "column" of pilots at the end of the burst
    tx = [states.tx_preamble tx states.tx_postamble];
  end
  
  % if burst mode concatenate multiple bursts with spaces
  if burst_mode
    atx = tx; tx = [];
    for b=1:Nbursts
      tx = [tx atx zeros(1,states.Fs)];
    end
    % adjust channel simulator SNR setpoint given (burst on length)/(total length including silence) ratio
    burst_len = length(atx); padded_burst_len = burst_len + states.Fs;
    mark_space_SNR_offset = 10*log10(burst_len/padded_burst_len);
    SNRdB_setpoint = SNR3kdB + mark_space_SNR_offset;
    printf("SNR3kdB: %4.2f Burst offset: %4.2f SNRdB_setpoint: %4.2f\n", SNR3kdB, mark_space_SNR_offset, SNRdB_setpoint)
  else
    SNRdB_setpoint = SNR3kdB; % no adjustment to SNR in streaming mode
  end

  printf("Npackets: %d  Nbursts: %d  ", Npackets, Nbursts);
  states.verbose=1;
  tx = ofdm_hilbert_clipper(states, tx, tx_clip_en);
  [rx_real rx] = ofdm_channel(states, tx, SNRdB_setpoint, channel, freq_offset_Hz);
  frx = fopen(filename,"wb"); fwrite(frx, rx_real, "short"); fclose(frx);
  if length(rx) >= states.Fs
    figure(1); clf; plot(20*log10(abs(fft(rx(1:states.Fs)/16384))));
    axis([1 states.Fs -20 60])
  end
endfunction
