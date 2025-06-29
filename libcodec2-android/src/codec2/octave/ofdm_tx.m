% ofdm_tx.m
% David Rowe March 2018
%
% File based, uncoded OFDM tx.  Generates a file of ofdm samples,
% including optional channel simulation.  See also ofdm_ldpc_tx.m, and
% ofdm_mod.c

#{
  Examples:

  i) 10 seconds, AWGN channel at SNR3k=3dB

    octave:4> ofdm_tx("awgn_snr_3dB_700d.raw", "700D", 10, 3)

  ii) 10 seconds, multipath poor channel at SNR=6dB

    octave:5> ofdm_tx("hf_snr_6dB_700d.raw", "700D", 10, 6, "mpp")
    
  iii) Data mode example, three bursts of one packet each, SNR=100dB:
  
    octave:6> ofdm_tx("test_datac0.raw","datac0",1,100,"awgn","bursts",3)

#}

function ofdm_tx(filename, mode="700D", N, SNR3kdB=100, channel='awgn', varargin)
  ofdm_lib;
  channel_lib;
  randn('seed',1);
  pkg load signal;

  tx_clip_en = 0; freq_offset_Hz = 0.0; burst_mode = 0; Nbursts = 1;
  i = 1;
  while i<=length(varargin)
    if strcmp(varargin{i},"txclip") 
      tx_clip_en = 1;
    elseif strcmp(varargin{i},"bursts") 
      burst_mode = 1;
      Nbursts = varargin{i+1}; i++;
    else
      printf("\nERROR unknown argument: [%d] %s \n", i ,varargin{i});
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

  % Generate fixed test frame of tx bits and concatentate packets

  tx_bits = create_ldpc_test_frame(states, coded_frame=0);
  atx = ofdm_mod(states, tx_bits);
  tx = [];
  for f=1:Npackets
    tx = [tx atx];
  end
  if length(states.data_mode)
    % note postamble provides a "column" of pilots at the end of the burst
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
  rx_real = ofdm_channel(states, tx, SNRdB_setpoint, channel, freq_offset_Hz);
  frx = fopen(filename,"wb"); fwrite(frx, rx_real, "short"); fclose(frx);
endfunction
