% qam16_test.m
% David Rowe May 2020
%
% Octave symbol rate QAM16/LDPC experiments

% Libraries we need

1;
qam16;
ldpc;

function test_qam16(fg=2)
  printf("QAM16 ----------------------------------------\n");

  mod_order = 16; bps = log2(mod_order);
  modulation = 'QAM'; mapping = ""; demod_type = 0; decoder_type = 0;
  max_iterations = 100; EsNo_dec = 10;
  qam16_const = [
    1 + j,  1 + j*3,  3 + j,  3 + j*3;
    1 - j,  1 - j*3,  3 - j,  3 - j*3;
   -1 + j, -1 + j*3, -3 + j, -3 + j*3;
   -1 - j, -1 - j*3, -3 - j, -3 - j*3];
  rms = sqrt(qam16_const(:)'*qam16_const(:)/16);
  qam16_const = qam16_const/rms;
  constellation_source = 'custom';
  test_qam16_mod_demod(qam16_const);
  
  load HRA_504_396.txt
  if strcmp(constellation_source,'cml')
    code_param = ldpc_init_user(HRA_504_396, modulation, mod_order, mapping);
  else
    code_param = ldpc_init_user(HRA_504_396, modulation, mod_order, mapping, reshape(qam16_const,1,16));
  end
  rate = code_param.ldpc_data_bits_per_frame/code_param.ldpc_coded_bits_per_frame;
   
  printf("EbNodB Tbits   Terrs BER   Tcbits  Tcerrs  Perrs CBER  CPER\n");
  EbNodBvec = 3:10; Ntrials = 1000;
  for i=1:length(EbNodBvec)
    EbNodB = EbNodBvec(i);
    EsNodB = EbNodB + 10*log10(rate) + 10*log10(bps); EsNodBvec(i) = EsNodB;
    EsNo = 10^(EsNodB/10);
    variance = 1/EsNo;
    Terrs = Tbits = 0; Tcerrs = 0; Tcbits = 0; Perrs = 0; rx_symbols_log = [];
    for nn = 1:Ntrials        
      tx_bits = round(rand(1, code_param.ldpc_data_bits_per_frame));
      [tx_codeword, tx_symbols] = ldpc_enc(tx_bits, code_param);
      noise = sqrt(variance*0.5)*(randn(1,length(tx_symbols)) + j*randn(1,length(tx_symbols)));
      rx_symbols = tx_symbols + noise;
      rx_symbols_log = [rx_symbols_log rx_symbols];

      % uncoded decode/demod and count errors
      rx_codeword = zeros(1,code_param.ldpc_coded_bits_per_frame);
      for s=1:length(rx_symbols)
        rx_codeword((s-1)*bps+1:s*bps) = qam16_demod(qam16_const,rx_symbols(s));
      end
      Nerr = sum(xor(tx_codeword,rx_codeword));
      Terrs += Nerr;
      Tbits += code_param.ldpc_coded_bits_per_frame;
      
      % LDPC demod/decode and count errors
      dec_rx_codeword = ldpc_dec(code_param, max_iterations, demod_type, decoder_type, rx_symbols, EsNo_dec, ones(1,length(rx_symbols)));
      errors_positions = xor(tx_bits, dec_rx_codeword(1:code_param.ldpc_data_bits_per_frame));
      Ncerr = sum(errors_positions);
      Tcbits += code_param.ldpc_data_bits_per_frame; Tcerrs += Ncerr;
      if Ncerr Perrs++; end
    end
    figure(fg); clf; plot(rx_symbols_log,"."); axis([-1.5 1.5 -1.5 1.5]); drawnow;
    printf("%5.1f %6d %6d %5.2f %6d %6d %6d  %5.2f %5.2f\n",
           EbNodB, Tbits, Terrs, Terrs/Tbits, Tcbits, Tcerrs, Perrs, Tcerrs/Tcbits, Perrs/Ntrials);
    ber(i) = Terrs/Tbits; cber(i) = Tcerrs/Tcbits; cper(i) = Perrs/Ntrials;
  end
  print("qam64_scatter.png","-dpng");

  figure(fg+1); clf; title('QAM16 Uncoded');
  uncoded_EbNodBvec = EbNodBvec + 10*log10(rate);
  ber_theory = ber_qam(uncoded_EbNodBvec);
  semilogy(uncoded_EbNodBvec,ber_theory,'b+-;uncoded QAM16 BER theory;','markersize', 10, 'linewidth', 2); hold on;
  semilogy(uncoded_EbNodBvec,ber+1E-10,'g+-;uncoded QAM16 BER;','markersize', 10, 'linewidth', 2); hold on;
  grid; axis([min(uncoded_EbNodBvec) max(uncoded_EbNodBvec) 1E-5 1]); xlabel('Uncoded Eb/No (dB)');
  print("qam16_uncoded_ber.png","-dpng");
  
  figure(fg+2); clf; title('QAM16 with LDPC (504,396)'); 
  semilogy(EbNodBvec,cber+1E-10,'b+-;QAM16 coded BER;','markersize', 10, 'linewidth', 2); hold on;
  semilogy(EbNodBvec,cper+1E-10,'g+-;QAM16 coded PER;','markersize', 10, 'linewidth', 2); hold off;
  grid; axis([min(EbNodBvec) max(EbNodBvec) 1E-5 1]); xlabel('Eb/No (dB)');

  figure(fg+3); clf; title('QAM16 with LDPC (504,396)'); 
  semilogy(EsNodBvec,cber+1E-10,'b+-;QAM16 coded BER;','markersize', 10, 'linewidth', 2); hold on;
  semilogy(EsNodBvec,cper+1E-10,'g+-;QAM16 coded PER;','markersize', 10, 'linewidth', 2); hold off;
  grid; axis([min(EsNodBvec) max(EsNodBvec) 1E-5 1]); xlabel('Es/No (dB)');
  print("qam16_504_396.png","-dpng");
endfunction


% Thanks Bill VK5DSP, for the QAM BER functions

function p = ber_qam(ebn0)
  % Calculate the bit error rate (BER) for square 16QAM in AWGN
  %   given the Eb/N0 in dB,  ebn0 can be a scalar or vector
  %   (assuming coherent detection, uncoded)
  %   [section 5.3 Webb and Hanzo text]

  e = 4*10.^(ebn0/10);    %  Es/N0 vector in linear
  b2 = qfn(sqrt(e/5));
  b1 = (qfn(sqrt(e/5)) + qfn(3*sqrt(e/5)))/2;
  p = (b1+b2)/2;
endfunction

function tail=qfn(a)
  % Usage: tail=qfn(a)
  % where: tail=area under the tail of the normal dist. from a to inf.
  %        for zero mean, unit variance distribution
  %
  % If no argument is given, plot Q(x) for x = 0 to 5

  % use erfc instead of 1-erf to avoid truncation errors!     April 2010

  fact = 1 / sqrt(2);
  if exist('a')

    % tail = 0.5 * ( 1 - erf(a * fact));
    tail = 0.5 * erfc(a * fact);
  else
    x=(0: 0.1: 6); semilogy(x,  0.5*( erfc(x * fact)));
    title('Q function plot');
    xlabel('x'); ylabel('Q(x)');
  end
endfunction


% --------------------------------------------------------------------------------
% START SIMULATIONS
% --------------------------------------------------------------------------------

more off;
format;

% Start CML library (see CML set up instructions in ldpc.m)
init_cml();

test_qam16(1)
