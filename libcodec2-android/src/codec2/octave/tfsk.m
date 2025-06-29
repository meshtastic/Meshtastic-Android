% tfsk.m
% Brady O'Brien 8 January 2016
% David Rowe May 2020
%
% Automatic testing of C port of FSK modem by comparing to reference
% Octave version. Currently just a subset of tests enabled in order to
% run in a reasonable amount of time as ctests, but still trapping any
% bit-rot.

#{

  FSK Modem automated test instructions:

  1. Use cmake to build in debug mode to ensure unittest/tfsk is built:

     $ cd ~/codec2
     $ rm -Rf build_linux && mkdir build_linux
     $ cd build_linux
     $ cmake -DCMAKE_BUILD_TYPE=Debug ..
     $ make

  2 - Change tfsk_location below if required
  3 - Ensure Octave packages are installed
  4 - Start Octave and run tfsk.m. It will perform all tests automatically

#}

% tfsk executable path/file
if getenv("PATH_TO_TFSK")
  global tfsk_location = getenv("PATH_TO_TFSK")
  printf("setting tfsk_location from env var: %s\n", getenv("PATH_TO_TFSK"));
else
  global tfsk_location = '../build_linux/unittest/tfsk';
end

% Set to 1 for verbose printouts
global print_verbose = 0;
global mod_pass_fail_maxdiff = 1e-3/5000;

fsk_horus_as_a_lib=1;
fsk_horus;
pkg load signal;
% not needed unless parallel tests running
%pkg load parallel;
graphics_toolkit('gnuplot');

function print_result(test_name, result)
  printf("%s", test_name);
  for i=1:(40-length(test_name))
    printf(".");
  end
  printf(": %s\n", result);  
end

function mod = fsk_mod_c(Fs,Rs,f1,fsp,bits,M)
    global tfsk_location;
    %command to be run by system to launch the modulator
    command = sprintf('%s M %d %d %d %d %d 0 fsk_mod_ut_bitvec fsk_mod_ut_modvec fsk_mod_ut_log.txt',tfsk_location,M,f1,fsp,Fs,Rs);
    %save input bits into a file
    bitvecfile = fopen('fsk_mod_ut_bitvec','wb+');
    fwrite(bitvecfile,bits,'uint8');
    fclose(bitvecfile);
    
    %run the modulator
    system(command);
    
    modvecfile = fopen('fsk_mod_ut_modvec','rb');
    mod = fread(modvecfile,'single');
    fclose(modvecfile);
    
endfunction


%Compare 2 vectors, fail if they are not close enough
function pass = vcompare(vc,voct,vname,tname,tol,pnum)
    global print_verbose;
    %Get delta of vectors
    dvec = abs(abs(vc - voct));     
    
    %Normalize difference
    dvec = dvec ./ abs(max(abs(voct))+1e-8);
    
    maxdvec = abs(max(dvec));
    pass = maxdvec<tol;
    if print_verbose == 1
        printf('  Comparing vectors %s in test %s. Diff is %f\n',vname,tname,maxdvec);
    end
    if pass == 0
        printf('\n*** vcompare failed %s in test %s. Diff: %f Tol: %f\n\n',vname,tname,maxdvec,tol);
        
        titlestr = sprintf('Diff between C and Octave of %s for %s',vname,tname)
        figure(10+pnum*2)
        plot(abs(dvec))
        title(titlestr)
        
        figure(11+pnum*2)
        plot((1:length(vc)),abs(vc),(1:length(voct)),abs(voct))
    
    end
    
endfunction

% Run C, then Octave verion of demod, and compare results
function test_stats = fsk_demod_xt(Fs,Rs,f1,fsp,mod,tname,M=2,lock_nin=0)
    global print_verbose;
    global tfsk_location;
    %Name of executable containing the modulator
    fsk_demod_ex_file = '../build/unittest/tfsk';
    modvecfilename = sprintf('fsk_demod_ut_modvec_%d',getpid());
    bitvecfilename = sprintf('fsk_demod_ut_bitvec_%d',getpid());
    tvecfilename = sprintf('fsk_demod_ut_tracevec_%d.txt',getpid());
    
    %command to be run by system to launch the demod
    command = sprintf('%s D %d %d %d %d %d %d %s %s %s',tfsk_location,M,f1,fsp,Fs,Rs,lock_nin,modvecfilename,bitvecfilename,tvecfilename);
    
    %save modulated input into a file
    modvecfile = fopen(modvecfilename,'wb+');
    fwrite(modvecfile,mod,'single');
    fclose(modvecfile);
    
    %run the modulator
    system(command);
    
    bitvecfile = fopen(bitvecfilename,'rb');
    bits = fread(bitvecfile,'uint8');
    fclose(bitvecfile);
    bits = bits!=0;
    
    %Load test vec dump
    load(tvecfilename)
    
    %Clean up files
    delete(bitvecfilename);
    delete(modvecfilename);
    delete(tvecfilename);
    
    o_f1_dc = [];
    o_f2_dc = [];
    o_f3_dc = [];
    o_f4_dc = [];
    o_f1_int = [];
    o_f2_int = [];
    o_f3_int = [];
    o_f4_int = [];
    o_f1 = [];
    o_f2 = [];
    o_f3 = [];
    o_f4 = [];
    o_EbNodB = [];
    o_ppm = [];
    o_Sf = [];
    o_fest = []; o_mask = []; o_fest2 = [];
    o_rx_timing = [];
    o_norm_rx_timing = [];
    o_nin = [];
    %Run octave demod, dump some test vectors
    states = fsk_horus_init(Fs,Rs,M);

    Ts = states.Ts;
    P = states.P;
    states.ftx(1) = f1;
    states.ftx(2) = f1+fsp;
    states.ftx(3) = f1+fsp*2;
    states.ftx(4) = f1+fsp*3;
    states.tx_tone_separation = fsp;
    states.dF = 0;
    modin = mod;
    obits = [];
    while length(modin)>=states.nin
        ninold = states.nin;
        states = est_freq(states, modin(1:states.nin), states.M);
        [bitbuf,states] = fsk_demod(states, modin(1:states.nin));
        if lock_nin states.nin = states.N; end

        modin=modin(ninold+1:length(modin));
        obits = [obits bitbuf];
        
        %Save other parameters
        o_f1_dc = [o_f1_dc states.f_dc(1,:)];
        o_f2_dc = [o_f2_dc states.f_dc(2,:)];
        o_f1_int = [o_f1_int states.f_int(1,:)];
        o_f2_int = [o_f2_int states.f_int(2,:)];
        o_EbNodB = [o_EbNodB states.EbNodB];
        o_ppm = [o_ppm states.ppm];
        o_rx_timing = [o_rx_timing states.rx_timing];
        o_norm_rx_timing = [o_norm_rx_timing states.norm_rx_timing];
        o_Sf = [o_Sf states.Sf'];
        o_f1 = [o_f1 states.f(1)];
        o_f2 = [o_f1 states.f(2)];
        o_fest = [o_fest states.f];
        o_mask = [o_mask states.mask];
        o_fest2 = [o_fest2 states.f2];
        o_nin = [o_nin states.nin];
        if M==4
            o_f3_dc = [o_f3_dc states.f_dc(3,:)];
	    o_f4_dc = [o_f4_dc states.f_dc(4,:)];
            o_f3_int = [o_f3_int states.f_int(3,:)];
            o_f4_int = [o_f4_int states.f_int(4,:)];
            o_f3 = [o_f1 states.f(3)];
            o_f4 = [o_f1 states.f(4)];
        end
    end
        
    assert(vcompare(o_Sf,  t_Sf,'fft est',tname,.001,1));
    assert(vcompare(o_fest,  t_f_est,'f est',tname,.001,2));
    assert(vcompare(o_mask,  t_mask,'f2 mask',tname,.001,3));
    assert(vcompare(o_fest2,  t_f2_est,'f2 est',tname,.001,16));
    o_fest2(1:12)
    t_f2_est(1:12)
    assert(vcompare(o_f1_dc,      t_f1_dc,    'f1 dc',    tname,.01,8));
    assert(vcompare(o_f2_dc,      t_f2_dc,    'f2 dc',    tname,.01,9));
    assert(vcompare(o_f2_int,     t_f2_int,   'f2 int',   tname,.01,10));
    assert(vcompare(o_f1_int,     t_f1_int,   'f1 int',   tname,.01,11));
    if M==4
        assert(vcompare(o_f3_dc,      t_f3_dc,    'f3 dc',    tname,.01,4))
        assert(vcompare(o_f4_dc,      t_f4_dc,    'f4 dc',    tname,.01,5));
        assert(vcompare(o_f3_int,     t_f3_int,   'f3 int',   tname,.01,6));
        assert(vcompare(o_f4_int,     t_f4_int,   'f4 int',   tname,.01,7));
    end
  
    assert(vcompare(o_rx_timing,  t_rx_timing,'rx timing',tname,.02,3));
 
    % Much larger tolerances on unimportant statistics
    assert(vcompare(o_ppm   ,     t_ppm,      'ppm',      tname,.02,12));
    assert(vcompare(o_EbNodB,     t_EbNodB,'EbNodB',      tname,.02,13));
    assert(vcompare(o_nin,        t_nin,      'nin',      tname,.0001,14));
    assert(vcompare(o_norm_rx_timing,  t_norm_rx_timing,'norm rx timing',tname,.02,15));
    diffpass = sum(xor(obits,bits'))<4;
    diffbits = sum(xor(obits,bits'));
        
    if diffpass==0
        printf('\n***bitcompare test failed test %s diff %d\n\n',tname,sum(xor(obits,bits')))
        figure(15)
        plot(xor(obits,bits'))
        title(sprintf('Bitcompare failure test %s',tname))
    end
    
    assert(diffpass);    
    
    test_stats.pass = 1;
    test_stats.diff = sum(xor(obits,bits'));
    test_stats.cbits = bits';
    test_stats.obits = obits;
endfunction


function [dmod,cmod,omod,pass] = fsk_mod_test(Fs,Rs,f1,fsp,bits,tname,M=2)
    global mod_pass_fail_maxdiff;
    %Run the C modulator
    cmod = fsk_mod_c(Fs,Rs,f1,fsp,bits,M);
    %Set up and run the octave modulator
    states.M = M;
    states = fsk_horus_init(Fs,Rs,M);
    
    states.ftx(1) = f1;
    states.ftx(2) = f1+fsp;
    
    if states.M == 4
        states.ftx(3) = f1+fsp*2;
        states.ftx(4) = f1+fsp*3;
    end
    
    states.dF = 0;
    omod = fsk_mod(states,bits);

    dmod = cmod-omod;
    pass = max(dmod)<(mod_pass_fail_maxdiff*length(dmod));
    if !pass
        printf('Mod failed test %s!\n',tname);
    end
endfunction

% Random bit modulator test
% Pass random bits through the modulators and compare
function pass = test_mod_horuscfg_randbits
    rand('state',1); 
    randn('state',1);
    bits = rand(1,10000)>.5;
    [dmod,cmod,omod,pass] = fsk_mod_test(8000,100,1200,1600,bits,"mod horuscfg randbits");
    
    if(!pass)
        figure(1)
        plot(dmod)
        title("Difference between octave and C mod impl");
    end
    print_result("test_mod_horuscfg_randbits", "OK");
endfunction

% Random bit modulator test
% Pass random bits through the modulators and compare
function pass = test_mod_horuscfgm4_randbits
    rand('state',1); 
    randn('state',1);
    bits = rand(1,10000)>.5;
    [dmod,cmod,omod,pass] = fsk_mod_test(8000,100,1200,1600,bits,"mod horuscfg randbits",4);
    
    if(!pass)
        figure(1)
        plot(dmod)
        title("Difference between octave and C mod impl");
    end
    print_result("test_mod_horuscfgm4_randbits", "OK");
    
endfunction


% A big ol' channel impairment tester shamelessly taken from fsk_horus
% This throws some channel imparment or another at the C and octave
% modem so they may be compared.
function stats = tfsk_run_sim(test_frame_mode,EbNodB,timing_offset,fading,df,M=2,frames=50,lock_nin=0)
  #{  
  timing_offset [0|1]  enable a 1000ppm sample clock offset
  fading        [0|1]  modulates tx power at 2Hz with 20dB fade depth, 
                       e.g. to simulate balloon rotating at end of mission
  df                   tx tone freq drift in Hz/s
  lock_nin      [0|1]  locks nin to a constant which makes tests much simpler by breaking feeback loop
  #}
  global print_verbose;
  
  more off
  rand('state',1); 
  randn('state',1);

  % ----------------------------------------------------------------------

  % sm2000 config ------------------------
  %states = fsk_horus_init(96000, 1200);
  %states.f1_tx = 4000;
  %states.f2_tx = 5200;
  
  if test_frame_mode == 2
    % horus rtty config ---------------------
    states = fsk_horus_init(8000, 100, M);
    states.f1_tx = 1200;
    states.f2_tx = 1600;
  end

  if test_frame_mode == 4
    % horus rtty config ---------------------
    states = fsk_horus_init(8000, 100, M);
    states.f1_tx = 1200;
    states.f2_tx = 1600;
    states.tx_bits_file = "horus_tx_bits_rtty.txt"; % Octave file of bits we FSK modulate
    
  end
                               
  if test_frame_mode == 5
    % horus binary config ---------------------
    states = fsk_horus_init(8000, 100, M);
    states.f1_tx = 1200;
    states.f2_tx = 1600;
    %%%states.tx_bits_file = "horus_tx_bits_binary.txt"; % Octave file of bits we FSK modulate
	states.tx_bits_file = "horus_payload_rtty.txt";
  end

  % ----------------------------------------------------------------------

  states.verbose = 0;
  N = states.N;
  P = states.P;
  Rs = states.Rs;
  nsym = states.nsym;
  Fs = states.Fs;
  states.df = df;
  states.M = M;

  EbNo = 10^(EbNodB/10);
  variance = states.Fs/(states.Rs*EbNo*states.bitspersymbol);

  % set up tx signal with payload bits based on test mode

  if test_frame_mode == 1
     % test frame of bits, which we repeat for convenience when BER testing
    test_frame = round(rand(1, states.nsym));
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
    % ...10101... sequence
    tx_bits = zeros(1, states.nsym*(frames+1));
    tx_bits(1:2:length(tx_bits)) = 1;
  end
 
  if (test_frame_mode == 4) || (test_frame_mode == 5)

    % load up a horus msg from disk and modulate that

    test_frame = load(states.tx_bits_file);
    ltf = length(test_frame);
    ntest_frames = ceil((frames+1)*nsym/ltf);
    tx_bits = [];
    for i=1:ntest_frames
      tx_bits = [tx_bits test_frame];
    end
  end
  
  f1 = states.f1_tx;
  fsp = states.f2_tx-f1;
  states.ftx(1) = f1;
  states.ftx(2) = f1+fsp;
    
  if states.M == 4
	states.ftx(3) = f1+fsp*2;
	states.ftx(4) = f1+fsp*3;
  end

  tx = fsk_mod(states, tx_bits);

  if timing_offset
    tx = resample(tx, 1000, 1001); % simulated 1000ppm sample clock offset
  end
  
  if fading
     ltx = length(tx);
     tx = tx .* (1.1 + cos(2*pi*2*(0:ltx-1)/Fs))'; % min amplitude 0.1, -20dB fade, max 3dB
  end

  noise = sqrt(variance)*randn(length(tx),1);
  rx    = tx + noise;
  
  test_name = sprintf("tfsk run sim EbNodB:%d frames:%d timing_offset:%d fading:%d df:%d",EbNodB,frames,timing_offset,fading,df);
  tstats = fsk_demod_xt(Fs,Rs,states.f1_tx,fsp,rx,test_name,M,lock_nin);
  
  pass = tstats.pass;
  obits = tstats.obits;
  cbits = tstats.cbits;
  
  % Figure out BER of octave and C modems
  bitcnt = length(tx_bits);
  rx_bits = obits;
  ber = 1;
  ox = 1;
  for offset = (1:100)
    nerr = sum(xor(rx_bits(offset:length(rx_bits)),tx_bits(1:length(rx_bits)+1-offset)));
    bern = nerr/(bitcnt-offset);
    if(bern < ber)
      ox = offset;
      best_nerr = nerr;
    end
    ber = min([ber bern]);
  end
  offset = ox;
  bero = ber;
  ber = 1;
  rx_bits = cbits;
  ox = 1;
  for offset = (1:100)
    nerr = sum(xor(rx_bits(offset:length(rx_bits)),tx_bits(1:length(rx_bits)+1-offset)));
    bern = nerr/(bitcnt-offset);
    if(bern < ber)
      ox = offset;
      best_nerr = nerr;
    end
    ber = min([ber bern]);
  end
  offset = ox;
  berc = ber;
  stats.berc = berc;
  stats.bero = bero;
  stats.name = test_name;
  % coherent BER theory calculation
  
  stats.thrcoh = .5*(M-1)*erfc(sqrt( (log2(M)/2) * EbNo ));
  
  % non-coherent BER theory calculation
  % It was complicated, so I broke it up

  ms = M;
  ns = (1:ms-1);
  as = (-1).^(ns+1);
  bs = (as./(ns+1));
  
  cs = ((ms-1)./ns);

  ds = ns.*log2(ms);
  es = ns+1;
  fs = exp( -(ds./es)*EbNo );
  
  thrncoh = ((ms/2)/(ms-1)) * sum(bs.*((ms-1)./ns).*exp( -(ds./es)*EbNo ));
  
  stats.thrncoh = thrncoh;
  stats.pass = pass;
endfunction

% run a bunch of tests at a range of EbNo's in parallel
function pass = ebno_battery_test(timing_offset,fading,df,M)
    %Range of EbNodB over which to test
    ebnodbrange = (5:2:13);
    ebnodbs = length(ebnodbrange);
    
    mode = 2;
    %Replication of other parameters for parcellfun
    modev   = repmat(mode,1,ebnodbs);
    timingv = repmat(timing_offset,1,ebnodbs);
    fadingv = repmat(fading,1,ebnodbs);
    dfv     = repmat(df,1,ebnodbs);
    mv      = repmat(M,1,ebnodbs);

    statv = pararrayfun(floor(1.25*nproc()),@tfsk_run_sim,modev,ebnodbrange,timingv,fadingv,dfv,mv);
    passv = zeros(1,length(statv));
    for ii=(1:length(statv))
        passv(ii)=statv(ii).pass;
        if statv(ii).pass
            printf("Test %s passed\n",statv(ii).name);
        else
            printf("Test %s failed\n",statv(ii).name);
        end
    end
    
    %All pass flags are '1'
    pass = sum(passv)>=length(passv);
    %and no tests died
    pass = pass && length(passv)==ebnodbs;
    passv;
    assert(pass)
endfunction

%Test with and without sample clock offset
function pass = test_timing_var(df,M)
    pass = ebno_battery_test(1,0,df,M)
    assert(pass)
    pass = pass && ebno_battery_test(0,0,df,M)
    assert(pass)
endfunction

%Test with and without 1 Hz/S freq drift
function pass = test_drift_var(M)
    pass = test_timing_var(1,1,M)
    pass = pass && test_timing_var(0,1,M)
    assert(pass)
endfunction

function pass = test_fsk_battery()
    pass = test_mod_horuscfg_randbits;
    pass = pass && test_mod_horuscfgm4_randbits;
    pass = pass && test_drift_var(4);
    assert(pass)
    pass = pass && test_drift_var(2);
    assert(pass)
    if pass
        printf("***** All tests passed! *****\n");
    end
endfunction

function plot_fsk_bers(M=2)
    %Range of EbNodB over which to plot
    ebnodbrange = (4:13);
    
    berc = ones(1,length(ebnodbrange));
    bero = ones(1,length(ebnodbrange));
    berinc = ones(1,length(ebnodbrange));
    beric = ones(1,length(ebnodbrange));
    ebnodbs = length(ebnodbrange)
    mode = 2;
    %Replication of other parameters for parcellfun
    modev   = repmat(mode,1,ebnodbs);
    timingv = repmat(1,1,ebnodbs);
    fadingv = repmat(0,1,ebnodbs);
    dfv     = repmat(1,1,ebnodbs);
    Mv     = repmat(M,1,ebnodbs);
        
    statv = pararrayfun(floor(nproc()),@tfsk_run_sim,modev,ebnodbrange,timingv,fadingv,dfv,Mv);
    %statv = arrayfun(@tfsk_run_sim,modev,ebnodbrange,timingv,fadingv,dfv,Mv);
    
    for ii = (1:length(statv))
        stat = statv(ii);
        berc(ii)=stat.berc;
        bero(ii)=stat.bero;
        berinc(ii)=stat.thrncoh;
        beric(ii) = stat.thrcoh;
    end
    clf;
    figure(M)
    
    semilogy(ebnodbrange, berinc,sprintf('r;%dFSK non-coherent theory;',M))
    hold on;
    semilogy(ebnodbrange, beric ,sprintf('g;%dFSK coherent theory;',M))
    semilogy(ebnodbrange, bero ,sprintf('b;Octave fsk horus %dFSK Demod;',M))
    semilogy(ebnodbrange, berc,sprintf('+;C fsk horus %dFSK Demod;',M))
    hold off;
    grid("minor");
    axis([min(ebnodbrange) max(ebnodbrange) 1E-5 1])
    legend("boxoff");
    xlabel("Eb/No (dB)");
    ylabel("Bit Error Rate (BER)")
 
endfunction

% We kick off tests here ------------------------------------------------------
   
pass = 0; ntests = 0;
pass += test_mod_horuscfg_randbits; ntests++;
pass += test_mod_horuscfgm4_randbits; ntests++;
stats = tfsk_run_sim(test_frame_mode=2,EbNodB=5,timing_offset=0,fading=0,df=1,M=4,frames=10,lock_nin=1); ntests++;
if stats.pass
  print_result("Demod 10 frames nin locked", "OK");
  pass += stats.pass; 
end
stats = tfsk_run_sim(test_frame_mode=2,EbNodB=5,timing_offset=1,fading=0,df=1,M=4,frames=10,lock_nin=0); ntests++;
if stats.pass
  print_result("Demod 10 frames", "OK");
  pass += stats.pass;
end
printf("tests: %d passed: %d ", ntests, pass);
if ntests == pass printf("PASS\n"); else printf("FAIL\n"); end
