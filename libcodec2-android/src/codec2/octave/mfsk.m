% mfsk.m
% David Rowe Nov 2015

% Simulation to test m=2 and m=4 FSK demod


1;

function sim_out = fsk_ber_test(sim_in)
  Fs        = 96000;
  M         = sim_in.M;
  Rs        = sim_in.Rs;
  Ts        = Fs/Rs;
  verbose   = sim_in.verbose;

  nbits     = sim_in.nbits;
  nsym      = sim_in.nbits*2/M;
  nsam      = nsym*Ts;
  EsNodB    = sim_in.EbNodB + 10*log10(M/2);

  % printf("M: %d nbits: %d nsym: %d\n", M, nbits, nsym);

  if M == 2
    f(1) = -Rs/2;
    f(2) =  Rs/2;
  end
  if M == 4
    f(1) = -3*Rs/2;
    f(2) = -Rs/2;
    f(3) =  Rs/2;
    f(4) =  3*Rs/2;
  end

  % simulate over a range of Eb/No values

  for ne = 1:length(EsNodB)
    Nerrs = Terrs = Tbits = 0;

    aEsNodB = EsNodB(ne);
    EsNo = 10^(aEsNodB/10);
    variance = Fs/(Rs*EsNo);

    % Modulator -------------------------------

    tx_bits = round(rand(1, nbits));
    tx = zeros(1,nsam);
    tx_phase = 0;

    for i=1:nsym
      if M == 2
        tone = tx_bits(i) + 1;
      else
        tone = (tx_bits(2*(i-1)+1:2*i) * [2 1]') + 1;
      end

      tx_phase_vec = tx_phase + (1:Ts)*2*pi*f(tone)/Fs;
      tx((i-1)*Ts+1:i*Ts) = exp(j*tx_phase_vec);
      tx_phase = tx_phase_vec(Ts) - floor(tx_phase_vec(Ts)/(2*pi))*2*pi;
    end
  
    % Channel ---------------------------------

    % We use complex (single sided) channel simulation, as it's convenient
    % for the FM simulation.

    noise = sqrt(variance/2)*(randn(1,nsam) + j*randn(1,nsam));
    rx    = tx + noise;
    if verbose > 1
      printf("EbNo: %f Eb: %f var No: %f EbNo (meas): %f\n", 
      EbNo, var(tx)*Ts/Fs, var(noise)/Fs, (var(tx)*Ts/Fs)/(var(noise)/Fs));
    end
 
    % Demodulator -----------------------------

    % non-coherent FSK demod

    rx_bb = rx;
    dc = zeros(M,nsam);
    for m=1:M
      dc(m,:) = rx_bb .* exp(-j*(0:nsam-1)*2*pi*f(m)/Fs);
    end

    rx_bits = zeros(1, nsym);
    for i=1:nsym
      st = (i-1)*Ts+1;
      en = st+Ts-1;
      for m=1:M
        int(m,i) = abs(sum(dc(m,st:en)));
      end
      if m == 2
        rx_bits(i) = int(1,i) < int(2,i);
      else
        [max_amp tone] = max([int(1,i) int(2,i) int(3,i) int(4,i)]);
        if tone == 1
          rx_bits(2*(i-1)+1:2*i) = [0 0];
        end
        if tone == 2
          rx_bits(2*(i-1)+1:2*i) = [0 1];
        end
        if tone == 3
          rx_bits(2*(i-1)+1:2*i) = [1 0];
        end
        if tone == 4
          rx_bits(2*(i-1)+1:2*i) = [1 1];
        end
     end
    end
  
    error_positions = xor(rx_bits, tx_bits);
    Nerrs = sum(error_positions);
    Terrs += Nerrs;
    Tbits += length(error_positions);

    TERvec(ne) = Terrs;
    BERvec(ne) = Terrs/Tbits;

    if verbose > 1
      figure(2)
      clf
      Rx = 10*log10(abs(fft(rx)));
      plot(Rx(1:Fs/2));
      axis([1 Fs/2 0 50]);

      figure(3)
      clf;
      subplot(211)
      plot(real(rx_bb(1:Ts*20)))
      subplot(212)
      Rx_bb = 10*log10(abs(fft(rx_bb)));
      plot(Rx_bb(1:3000));
      axis([1 3000 0 50]);

      figure(4);
      subplot(211)
      stem(abs(mark_int(1:100)));
      subplot(212)
      stem(abs(space_int(1:100)));   
    end

    if verbose
      printf("EbNo (db): %3.2f Terrs: %d BER: %4.3f \n", aEsNodB - 10*log10(M/2), Terrs, Terrs/Tbits);
    end
  end

  sim_out.TERvec = TERvec;
  sim_out.BERvec = BERvec;
endfunction


function run_fsk_curves
  sim_in.M         = 2;
  sim_in.Rs        = 1200;
  sim_in.nbits     = 12000;
  sim_in.EbNodB    = 0:2:20;
  sim_in.verbose   = 1;
  
  EbNo  = 10 .^ (sim_in.EbNodB/10);
  fsk_theory.BERvec = 0.5*exp(-EbNo/2); % non-coherent BFSK demod
  fsk2_sim = fsk_ber_test(sim_in);

  sim_in.M = 4;
  fsk4_sim = fsk_ber_test(sim_in);

  % BER v Eb/No curves

  figure(1); 
  clf;
  semilogy(sim_in.EbNodB, fsk_theory.BERvec,'r;2FSK theory;')
  hold on;
  semilogy(sim_in.EbNodB, fsk2_sim.BERvec,'g;2FSK sim;')
  semilogy(sim_in.EbNodB, fsk4_sim.BERvec,'b;4FSK sim;')
  hold off;
  grid("minor");
  axis([min(sim_in.EbNodB) max(sim_in.EbNodB) 1E-4 1])
  legend("boxoff");
  xlabel("Eb/No (dB)");
  ylabel("Bit Error Rate (BER)")

end


function run_fsk_single
  sim_in.M         = 4;
  sim_in.Rs        = 1200;
  sim_in.nbits     = 5000;
  sim_in.EbNodB    = 8;
  sim_in.verbose   = 1;

  fsk_sim = fsk_ber_test(sim_in);
endfunction


rand('state',1); 
randn('state',1);
graphics_toolkit ("gnuplot");

run_fsk_curves
%run_fsk_single

