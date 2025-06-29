% fsk_llr_test.m
%
% 2/4FSK simulation to develop LLR estimation algorithms for 4FSK/LDPC modems
% Modified version of David's fsk_llr.m;   Bill 

#{
  TODO
  The 'v' param of the Ricean pdf is the signal-only amplitude: genie value=16 
  In practice, given varying input levels, this value needs to be estimated.

  A small scaling factor seems to improve 2FSK performance -- probably the 'sig'
  estimate can be improved.    

  Only tested with short code -- try a longer one!  

  Simulation should be updated to exit Eb after given Nerr reached

#}

ldpc;

% define Rician pdf
function y = rice(x,v,s)
  s2 = s*s; 
  y = (x / s2) .* exp(-0.5 * (x.^2 + v.^2)/s2) .* besseli(0, x*v/s2);
endfunction

function plot_pdf(v,s)
  x=(0:0.1:2*v); 
  y= rice(x, v, s); 
  figure(201);   hold on 
  plot(x,y,'g');
  %title('Rician pdf: signal carrier')
  y= rice(x, 0, s); 
  plot(x,y,'b');
  title('Rician pdf: signal and noise-only carriers')
  pause(0.01); 
endfunction 
  
% single Eb/No point simulation    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

function [raw_ber rx_filt rx_bits tx_symbols demapper sig_est ] = run_single(tx_bits, M, EcNodB,  plt=0)
  % Ec/N0 is per channel bit
  bps = log2(M);  % bits per symbol
  Ts = 16;        % length of each symbol in samples

  if length(tx_bits)==1
     Nbits = tx_bits;  
     tx_bits = randi(2,1,Nbits)-1;    % make random bits 
  endif 

  Nbits = length(tx_bits);
  Nsymbols = Nbits/log2(M);
  tx_symbols = zeros(1,Nsymbols); 

  mapper = bps:-1:1;
  % look up table demapper from symbols to bits (hard decision) 
  demapper=zeros(M,bps);
  for m=1:M
    for b=1:bps
      if  bitand(m-1,b) demapper(m,bps-b+1) = 1; end
    end
  end
  
  % continuous phase mFSK modulator

  w(1:M) = 2*pi*(1:M)/Ts;
  tx_phase = 0;
  tx = zeros(1,Ts*Nsymbols);

  for s=1:Nsymbols
    bits_for_this_symbol = tx_bits(bps*(s-1)+1:bps*s);
    symbol_index = bits_for_this_symbol * mapper' + 1;
    tx_symbols(s) = symbol_index; 
    assert(demapper(symbol_index,:) == bits_for_this_symbol);
    for k=1:Ts
      tx_phase += w(symbol_index);
      tx((s-1)*Ts+k) = exp(j*tx_phase);
    end
  end

  % AWGN channel noise

  
  EsNodB = EcNodB + 10*log10(bps)
  EsNo = 10^(EsNodB/10);
  variance = Ts/EsNo;
  noise = sqrt(variance/2)*(randn(1,Nsymbols*Ts) + j*randn(1,Nsymbols*Ts));
  rx = tx + noise;

  if plt==2,    % check the Spectrum 
    [psd,Fpsd] =pwelch(rx,128,0.5,128,Ts);
    figure(110); plot(Fpsd,10*log10(psd));
    title('Rx Signal:  PSD '); 
    xlabel('Freq/Rs');
    %figure(111);plot(unwrap(arg(tx)));
    pause(0.01);
  endif 


  % integrate and dump demodulator

  rx_bits = zeros(1,Nbits);
  rx_filt = zeros(Nsymbols,M);
  rx_pows = zeros(1,M); 
  rx_nse_pow  = 0.0; rx_sig_pow =0.0; 
  for s=1:Nsymbols
    arx_symb = rx((s-1)*Ts + (1:Ts));
    for m=1:M
      r= sum(exp(-j*w(m)*(1:Ts)) .* arx_symb);
      rx_pows(m)= r * conj(r); 
      rx_filt(s,m) = abs(r);
    end
    [tmp symbol_index] = max(rx_filt(s,:));
    rx_sig_pow = rx_sig_pow + rx_pows(symbol_index);
    rx_pows(symbol_index)=[];
    rx_nse_pow = rx_nse_pow + sum(rx_pows)/(M-1); 
    rx_bits(bps*(s-1)+1:bps*s) = demapper(symbol_index,:);
  end
  % using Rxpower = v^2 + sigmal^2

  rx_sig_pow = rx_sig_pow/Nsymbols; 
  rx_nse_pow = rx_nse_pow/Nsymbols;
  sig_est = sqrt(rx_nse_pow/2)    % for Rayleigh: 2nd raw moment = 2 .sigma^2
  Kest = rx_sig_pow/(2.0*sig_est^2) -1.0   
  
  Nerrors = sum(xor(tx_bits, rx_bits));
  raw_ber = Nerrors/Nbits;
  printf("EcNodB: %4.1f  M: %2d Uncoded Nbits: %5d Nerrors: %4d (Raw) BER: %1.3f\n", ...
          EcNodB, M, Nbits, Nerrors, raw_ber);
  if plt==1, plot_hist(rx_filt,tx_symbols, M); end 

endfunction


% Plot histograms of Rx filter outputs
function plot_hist(rx_filt,tx_symbols, M)
   % more general version of previous fn; - plots histograms for any Tx patterns
   Smax = 36;  
   X = 0:Smax-1;
   H = zeros(1,Smax);  H2 = zeros(1,Smax); s2=0.0;  
   for m = 1:M
     ind = tx_symbols==m;
     ind2 =  tx_symbols~=m;
     H= H+ hist(rx_filt(ind,m),X);
     H2= H2+ hist(rx_filt(ind2,m),X);
     x=rx_filt(ind2,m);
     s2 =s2 + sum(x(:).^2)/length(x);
   end 
   disp('noise RMS is ');  sqrt(s2/4)
   figure(1);  clf; plot(X,H);
   title([num2str(M) 'FSK pdf for rx=tx symbol'])   
   figure(2); clf;  plot(X,H2); 
   title([num2str(M) 'FSK pdf for rx!=tx symbol'])
   pause(0.1); 
  
endfunction  

% 2FSK SD -> LLR mapping that we used for Wenet SSTV system
function llr = sd_to_llr(sd, HD=0)     %    original 2FSK  + HD option  
    sd = sd / mean(abs(sd));
    x = sd - sign(sd);
    sumsq = sum(x.^2);
    summ = sum(x);
    mn = summ/length(sd);
    estvar = sumsq/length(sd) - mn*mn; 
    estEsN0 = 1/(2* estvar + 1E-3); 
    if HD==0, 
      llr = 4 * estEsN0 * sd;
    else 
       llr = 4 * estEsN0 * sign(sd);
    endif
endfunction


% single point LDPC encoded frame simulation, usin 2FSK as a tractable starting point
% Note: ~/cml/matCreateConstellation.m has some support for FSK - can it do 4FSK?

%%%%%%%%%%%%%%%%%%%%%%%%%
  function [Nerrors raw_ber EcNodB] = run_single_ldpc(M, Ltype, Nbits,EbNodB, plt=0)
  
  disp([num2str(M) 'FSK coded test ...  '])
  if M==2 
    bps = 1; modulation = 'FSK'; mod_order=2; mapping = 'gray'; 
  elseif M==4 
    bps = 2; modulation = 'FSK'; mod_order=4; mapping = 'gray';
  else
    error('sorry - bad value of M!');  
  endif
  decoder_type = 0; max_iterations = 100;

  load H_256_768_22.txt
  Krate = 1/3; 
  EcNodB = EbNodB + 10*log10(Krate); 
  code_param = ldpc_init_user(H_256_768_22, modulation, mod_order, mapping);
  Nframes = floor(Nbits/code_param.data_bits_per_frame)
  Nbits = Nframes*code_param.data_bits_per_frame

  % Encoder
  data_bits = round(rand(1,code_param.data_bits_per_frame));
  tx_bits = [];
  for f=1:Nframes;
    codeword_bits = LdpcEncode(data_bits, code_param.H_rows, code_param.P_matrix);
    tx_bits = [tx_bits codeword_bits];
  end
  %tx_bits = zeros(1,length(tx_bits));

  % modem/channel simulation
  [raw_ber rx_filt rx_bits tx_symbols demapper sig_est ] = run_single(tx_bits,M,EcNodB, 0 );

  % Decoder
  Nerrors = 0;
  for f=1:Nframes
    st = (f-1)*code_param.coded_bits_per_frame/bps + 1;
    en = st + code_param.coded_bits_per_frame/bps - 1;
    
    if or(Ltype==1, Ltype==3)
        if bps==1, 
	  sd = rx_filt(st:en,1) - rx_filt(st:en,2);
          %  OR ind = rx_filt(st:en,1) > rx_filt(st:en,2);
          %     llr = ind'*2 -1;   % this works but use SNR scaling  
          if Ltype==3, HD=1; else, HD = 0;  endif
          llr = sd_to_llr(sd, HD)'; 
        endif  
        if bps==2,
           if Ltype==3, 
	  llr = mfsk_hd_to_llrs(rx_filt(st:en,:), demapper); 
	   else 
              error('Ltype =1 not provided for coded 4FSK');
           endif 
         endif            
    endif
    if Ltype==2,     % SDs are converted to LLRs 
       v=16;
       if plt==1, plot_pdf(v, sig_est);  endif   
       llr = mfsk_sd_to_llrs(rx_filt(st:en,:), demapper, v, sig_est);
    endif

    [x_hat, PCcnt] = MpDecode(llr, code_param.H_rows, code_param.H_cols, ...
                            max_iterations, decoder_type, 1, 1);      
    Niters = sum(PCcnt!=0);
    detected_data = x_hat(Niters,:);
    Nerrors += sum(xor(data_bits, detected_data(1:code_param.data_bits_per_frame)));
  endfor
  ber = Nerrors/Nbits;
  printf("EbNodB: %4.1f  Coded   Nbits: %5d Nerrors: %4d BER: %1.3f\n", EbNodB, Nbits, Nerrors, ber);
endfunction

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

%rand('seed',1);
%randn('seed',1);
format short
more off
init_cml();

% store results in array "res" and plot afterwards
% comment the following line if you want to retain prev sims 
nrun = 0; clear res;   

Nbits = 20000;  plt=0;    

#{
disp(' uncoded runs')
for M=  [2 4]  
for Eb = [6:10]
  raw_ber = run_single(Nbits, M,  Eb, plt)    % 2fsk coded 
   nrun = nrun+1; res(nrun,:) =  [Eb Eb M -1 Nbits -1  raw_ber]
endfor
endfor
#} 

disp(' coded runs '); 

M=2,  
for Ltype = [1 2 3] 		    
for Eb = [7: 0.5: 9]
  [Nerr raw_ber Ec] = run_single_ldpc(M, Ltype, Nbits, Eb, plt)   
   nrun = nrun+1; res(nrun,:) =  [Eb Ec M Ltype Nbits Nerr raw_ber]
endfor
endfor

M=4,   %v=16; 
for Ltype = [2 3] 		    
for Eb = [8.0 8.3 8.6 ] 
  [Nerr raw_ber Ec] = run_single_ldpc(M, Ltype, Nbits, Eb, plt)   
   nrun = nrun+1; res(nrun,:) =  [Eb Ec M Ltype Nbits Nerr raw_ber]
endfor
endfor

		    
date = datestr(now)
save 'mfsk_test_res.mat'  res date

