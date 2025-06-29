% esno_est.m
% David Rowe Mar 2017
%
% Functions for esimating Es/No from QPSK symbols, an dsupporting tests

1;

#{
  ----------------------------------------------------------------------------
  Estimate the energy and noise of received symbols.
  
  Signal power is distance from axis on complex
  plane. Noise power is the distance orthogonal to each symbol, to provide an
  estimate that is insensitive to fading that moves symbol towards he origin.
  
  For QAM we need to use pilots as they don't have modulation that affects 
  estimate, for QPSK Modes we can use all rx symbols.
  ----------------------------------------------------------------------------
#}

function EsNodB = esno_est_calc(rx_syms)
  sig_var = sum(abs(rx_syms) .^ 2)/length(rx_syms);
  sig_rms = sqrt(sig_var);
   
  sum_x = 0;
  sum_xx = 0;
  n = 0;
  for i=1:length(rx_syms)
    s = rx_syms(i);
    % only consider symbols a reasonable distance from the origin, as these are
    % more likely to be valid and not errors that will mess up the estimate
    if abs(s) > sig_rms
        % rough demodulation, determine if symbol is on real or imag axis
        if abs(real(s)) > abs(imag(s))
          % assume noise is orthogonal to real axis
          sum_x  += imag(s);
          sum_xx += imag(s)*imag(s);
        else
          % assume noise is orthogonal to imag axis
          sum_x  += real(s);
          sum_xx += real(s)*real(s);
        end
        n++;
    end
  end

  % trap corner case
  if n > 1
    noise_var = (n*sum_xx - sum_x*sum_x)/(n*(n-1));
  else
    noise_var = sig_var;
  end

  % Total noise power is twice estimate of single-axis noise.
  noise_var = 2*noise_var;
  
  EsNodB = 10*log10(sig_var/noise_var);  
endfunction


#{
  Plot curves of Es/No estimator in action.
  
  Plots indicate it works OK down to Es/No=3dB,
  where it is 1dB high.  That's acceptable as Es/No=3dB is the lower limit of 
  our operation (ie Eb/No=0dB, 10% raw BER).
#}

function [EsNo_est rx_symbols] = esno_est_curves(EsNodB=0:20, channel="awgn", plot_en=1)
    Nsym=1000; rand('seed',1); randn('seed',1);
    tx_symbols = 2*(rand(1,Nsym) > 0.5) -1 + j*(2*(rand(1,Nsym) > 0.5) - 1);
    tx_symbols *= exp(-j*pi/4)/sqrt(2);
    
    if strcmp(channel,"mpp")
        % for fading we assume perfect phase recovery, so just multiply by mag
        spread = doppler_spread(2.0, 50, length(tx_symbols));
        tx_symbols = tx_symbols .* abs(spread);
        % normalise power over the multipath channel run
        S = tx_symbols*tx_symbols';
        tx_symbols *= sqrt(Nsym/S);
    end
    
    for i = 1:length(EsNodB)
        aEsNodB = EsNodB(i);
        EsNo = 10 .^ (aEsNodB/10);
        N = 1/EsNo;
        noise = sqrt(N/2)*randn(1,Nsym) +  sqrt(N/2)*j*randn(1,Nsym);  
        S = tx_symbols*tx_symbols';
        N = noise*noise';
        EsNo_meas(i) = 10*log10(S/N);
        rx_symbols = tx_symbols + noise;  
        EsNo_est(i) = esno_est_calc(rx_symbols);
        printf("EsNo: %5.2f EsNo_meas: %5.2f EsNo_est: %5.2f\n", aEsNodB, EsNo_meas(i), EsNo_est(i));
    end
    if plot_en
        figure(1);
        plot(EsNodB, EsNo_meas, '+-;EsNo meas;');  hold on; plot(EsNodB, EsNo_est, 'o-;EsNo est;'); hold off;
        axis([0 max(EsNodB) 0 max(EsNodB)]); grid;
        figure(2); plot(tx_symbols,'+');
    end
endfunction

function esno_est_test(channel="awgn")
    test_point_dB = 5;
    [EsNo_est_awgn rx_syms] = esno_est_curves(test_point_dB, channel, plot_en=0);
    if abs(EsNo_est_awgn - test_point_dB) < 1.0
        printf("%s Pass\n",toupper(channel));
    else
        printf("%s Fail\n",toupper(channel));
    end
endfunction

function esno_est_tests_octave
    esno_est_test("awgn");
    esno_est_test("mpp");    
endfunction

function esno_est_test_c(channel="awgn")
    test_point_dB = 5;
    [EsNo_est rx_syms] = esno_est_curves(test_point_dB, channel, plot_en=0);
    rx_syms_float = zeros(1,2*length(rx_syms));
    rx_syms_float(1:2:end) = real(rx_syms);
    rx_syms_float(2:2:end) = imag(rx_syms);
    f = fopen("esno_est.iqfloat","wb"); fwrite(f, rx_syms_float, "float"); fclose(f);

    printf("\nRunning C version....\n");
    path_to_unittest = "../build_linux/unittest"
    if getenv("PATH_TO_UNITEST")
      path_to_unittest = getenv("PATH_TO_UNITEST")
      printf("setting path from env var to %s\n", path_to_unittest);
    end
    system(sprintf("%s/tesno_est %s %d > tesno_est_out.txt", path_to_unittest, "esno_est.iqfloat", length(rx_syms)));
    load tesno_est_out.txt;
    printf("test_point: %5.2f Octave: %5.2f C: %5.2f\n", test_point_dB, EsNo_est, tesno_est_out);
    if abs(EsNo_est - tesno_est_out) < 0.5
        printf("%s Pass\n",toupper(channel));
    else
        printf("%s Fail\n",toupper(channel));
    end
endfunction

function esno_est_tests_c
    esno_est_test_c("awgn");
    esno_est_test_c("mpp");    
endfunction


