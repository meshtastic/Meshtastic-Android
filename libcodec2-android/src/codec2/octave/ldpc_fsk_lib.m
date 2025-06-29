% lpdc_fsk_lib.m
% April 2015
%
% Library version of ldpc4.m written by vk5dsp.  Application is high bit rate
% balloon telemtry
%
% LDPC demo
% Call the CML routines and simulate one set of SNRs.  
% This fucntion is an updated version of ldpc3() which uses less 
% of the CML functions 
%
% sim_in the input parameter structure
% sim_out contains BERs and other stats for each value of SNR
% resfile is the result file
%

1;

function sim_out = ldpc5(sim_in, resfile, testmode, genie_Es, logging=0);

    if nargin<4, testmode = 0; end
    estEsN0 = 0; 

    HRA       = sim_in.HRA;
    framesize = sim_in.framesize;
    rate      = sim_in.rate;
    mod_order = sim_in.mod_order;

    Lim_Ferrs = sim_in.Lim_Ferrs;
    Ntrials   = sim_in.Ntrials;
    Esvec     = sim_in.Esvec;

    demod_type = 0;
    decoder_type = 0;
    max_iterations = 100;
    code_param = ldpc_init(HRA, mod_order);
    bps = code_param.bits_per_symbol;


    if (logging) 
       fod = fopen('decode.log', 'w'); 
       fwrite(fod, 'Es estEs Its  secs \n'); 
    end 


    for ne = 1:length(Esvec)
        Es = Esvec(ne);
        EsNo = 10^(Es/10);


        Terrs = 0;  Tbits =0;  Ferrs =0;
        for nn = 1: Ntrials

          data = round( rand( 1, code_param.data_bits_per_frame ) );
          codeword = ldpc_encode(code_param, data);

          code_param.code_bits_per_frame = length( codeword );
          Nsymb = code_param.code_bits_per_frame/bps;

            if testmode==1
               f1 = fopen("dat_in2064.txt", "w");
               for k=1:length(data);  fprintf(f1, "%u\n", data(k)); end
               fclose(f1); 
               system("./ra_enc"); 

               load("dat_op2064.txt"); 
               pbits = codeword(length(data)+1:end);   %   print these to compare with C code 
               dat_op2064(1:16)', pbits(1:16)  
               differences_in_parity =  sum(abs(pbits - dat_op2064'))
               pause;  
            end


            % modulate
            % s = Modulate( codeword, code_param.S_matrix );
            s= 1 - 2 * codeword;   
            code_param.symbols_per_frame = length( s );

            variance = 1/(2*EsNo);
            noise = sqrt(variance)* randn(1,code_param.symbols_per_frame); 
            %  +  j*randn(1,code_param.symbols_per_frame) );
            r = s + noise;
            Nr = length(r);  

            [detected_data Niters] = ldpc_decode(r, code_param, max_iterations, decoder_type);

            error_positions = xor( detected_data(1:code_param.data_bits_per_frame), data );
            Nerrs = sum( error_positions);

            t = clock;   t =  fix(t(5)*60+t(6)); 
            if (logging)  
              fprintf(fod, ' %3d  %4d\n', Niters, t); 
              end 

            if Nerrs>0, fprintf(1,'x'),  else fprintf(1,'.'),  end
            if (rem(nn, 50)==0),  fprintf(1,'\n'),  end

            if Nerrs>0,  Ferrs = Ferrs +1;  end
            Terrs = Terrs + Nerrs;
            Tbits = Tbits + code_param.data_bits_per_frame;

            if Ferrs > Lim_Ferrs, disp(['exit loop with #cw errors = ' ...
                    num2str(Ferrs)]);  break,  end
        end

        TERvec(ne) = Terrs;
        FERvec(ne) = Ferrs;
        BERvec(ne) = Terrs/ Tbits;
        Ebvec = Esvec - 10*log10(code_param.bits_per_symbol * rate);

        cparams= [code_param.data_bits_per_frame  code_param.symbols_per_frame ...
            code_param.code_bits_per_frame];

        sim_out.BERvec = BERvec;
        sim_out.Ebvec = Ebvec;
        sim_out.FERvec = FERvec;
        sim_out.TERvec  = TERvec;
        sim_out.cpumins = cputime/60;

        if nargin > 2
            save(resfile,  'sim_in',  'sim_out',  'cparams');
            disp(['Saved results to ' resfile '  at Es =' num2str(Es) 'dB']);
        end
      end
end


function code_param = ldpc_init(HRA, mod_order)
  code_param.bits_per_symbol = log2(mod_order);
  [H_rows, H_cols] = Mat2Hrows(HRA); 
  code_param.H_rows = H_rows; 
  code_param.H_cols = H_cols;
  code_param.P_matrix = [];
  code_param.data_bits_per_frame = length(code_param.H_cols) - length( code_param.P_matrix ); 
  code_param.symbols_per_frame = length(HRA);
end


function codeword = ldpc_encode(code_param, data)
      codeword = LdpcEncode( data, code_param.H_rows, code_param.P_matrix );
endfunction


% Takes soft decision symbols (e.g. output of 2fsk demod) and converts
% them to LLRs.  Note we calculate mean and var manually instead of
% using internal functions.  This was required to get a bit exact
% results against the C code.

function llr = sd_to_llr(sd)
    sd = sd / mean(abs(sd));
    x = sd - sign(sd);
    sumsq = sum(x.^2);
    summ = sum(x);
    mn = summ/length(sd);
    estvar = sumsq/length(sd) - mn*mn; 
    estEsN0 = 1/(2* estvar + 1E-3); 
    llr = 4 * estEsN0 * sd;
endfunction


% LDPC decoder - note it estimates EsNo from received symbols

function [detected_data Niters] = ldpc_decode(r, code_param, max_iterations, decoder_type)
  % in the binary case the LLRs are just a scaled version of the rx samples ..

 #{
  r = r / mean(abs(r));       % scale for signal unity signal  
  estvar = var(r-sign(r)); 
  estEsN0 = 1/(2* estvar + 1E-3); 
  input_decoder_c = 4 * estEsN0 * r;
 #}
  llr = sd_to_llr(r);

  [x_hat, PCcnt] = MpDecode(llr, code_param.H_rows, code_param.H_cols, ...
                            max_iterations, decoder_type, 1, 1);         
  Niters = sum(PCcnt!=0);
  detected_data = x_hat(Niters,:);
  
  if isfield(code_param, "c_include_file")
    ldpc_gen_h_file(code_param, max_iterations, decoder_type, llr, x_hat, detected_data);
  end
end


% One application of FSK LDPC work is SSTV.  This function generates a
% simulated frame for testing

function frame_rs232 = gen_sstv_frame
  load('H2064_516_sparse.mat');
  HRA = full(HRA);  
  mod_order = 2;
  code_param = ldpc_init(HRA, mod_order);

  % generate payload data bytes and checksum

  data = floor(rand(1,256)*256);
  %data = zeros(1,256);
  checksum = crc16(data);
  data = [data hex2dec(checksum(3:4)) hex2dec(checksum(1:2))];

  % unpack bytes to bits and LPDC encode

  mask = 2.^(7:-1:0);       % MSB to LSB unpacking to match python tx code.
  unpacked_data = [];
  for b=1:length(data)
    unpacked_data = [unpacked_data bitand(data(b), mask) > 0];
  end
  codeword = [ldpc_encode(code_param, unpacked_data) 0 0 0 0];  % pad with 0s to get integer number of bytes

  % pack back into bytes to match python code 

  lpacked_codeword = length(codeword)/8;
  packed_codeword = zeros(1,lpacked_codeword);
  for b=1:lpacked_codeword
    st = (b-1)*8 + 1;
    packed_codeword(b) = sum(codeword(st:st+7) .* mask);
  end

  % generate header bits

  header = [hex2dec('55')*ones(1,16) hex2dec('ab') hex2dec('cd') hex2dec('ef') hex2dec('01')];

  % now construct entire unpacked frame

  packed_frame = [header packed_codeword];
  mask = 2.^(0:7);        % LSB to MSB packing for header
  lpacked_frame = length(packed_frame);
  frame = [];
  for b=1:lpacked_frame
    frame = [frame bitand(packed_frame(b), mask) > 0];
  end

  % insert rs232 framing bits

  frame_rs232 = [];
  for b=1:8:length(frame)
    frame_rs232 = [frame_rs232 0 frame(b:b+7) 1];
  end

  %printf("codeword: %d unpacked_header: %d frame: %d frame_rs232: %d \n", length(codeword), length(unpacked_header), length(frame), length(frame_rs232));
endfunction


% calculates and compares the checksum of a SSTV frame, that has RS232
% start and stop bits

function checksum_ok = sstv_checksum(frame_rs232)
  l = length(frame_rs232);
  expected_l = (256+2)*10;
  assert(l == expected_l);

  % extract rx bytes

  rx_data = zeros(1,256);
  mask = 2.^(0:7);          % LSB to MSB
  k = 1;
  for i=1:10:expected_l
    rx_bits = frame_rs232(i+1:i+8);
    rx_data(k) = sum(rx_bits .* mask); 
    k++;
  end

  % calc rx checksum and extract tx checksum

  rx_checksum = crc16(rx_data(1:256));
  tx_checksum = sprintf("%02X%02X", rx_data(258), rx_data(257));
  %printf("tx_checksum: %s rx_checksum: %s\n", tx_checksum, rx_checksum);
  checksum_ok = strcmp(tx_checksum, rx_checksum);
endfunction
