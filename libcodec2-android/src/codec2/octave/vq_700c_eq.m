% vq_700c.m
% David Rowe May 2019
%
% Researching Codec 2 700C VQ equaliser ideas
% See also scripts/train_700c_quant.sh, tnewamp1.m

melvq;
newamp_700c;

% general purpose plot function for looking at averages of K-band
% sequences in scripts dir and VQs:
%   vq_700c_plots({"hts2a.f32" "vk5qi.f32" "train_120_1.txt"})

function vq_700c_plots(fn_array)
  K = 20; rate_K_sample_freqs_kHz = mel_sample_freqs_kHz(K);
  freq_Hz = rate_K_sample_freqs_kHz * 1000;
  
  figure(1); clf; hold on; axis([200 4000 40 90]); title('Max Hold');
  figure(2); clf; hold on; axis([200 4000  0 40]); title('Average'); 
  
  for i=1:length(fn_array)
    [dir name ext] = fileparts(fn_array{i});
    if strcmp(ext, ".f32")
      % f32 feature file
      fn = sprintf("../build_linux/%s%s", name, ext)
      bands = load_f32(fn , K);
    else
      % text file (e.g. existing VQ)
      bands = load(fn_array{i});
    end
    % for max hold: break into segments of Nsec, find max, average maximums
    % this avoids very rare global peaks setting the max
    Nsec = 10; Tframe = 0.01; frames_per_seg = Nsec/Tframe
    Nsegs = floor(length(bands)/frames_per_seg)
    max_holds = zeros(Nsegs, K);
    if Nsegs == 0
       max_holds = max(bands)
    else
      for s=1:Nsegs
        st = (s-1)*frames_per_seg+1; en = st + frames_per_seg - 1;
        max_holds(s,:) = max(bands(st:en,:));
      end
      max_holds = mean(max_holds);
    end
    figure(1); plot(freq_Hz, max_holds, '+-', 'linewidth', 2);
    figure(2); plot(freq_Hz, mean(bands), '+-', 'linewidth', 2);
  end
  figure(1); legend(fn_array); grid; xlabel('Freq (Hz)'); ylabel('Amp dB');
  figure(2); legend(fn_array); grid; xlabel('Freq (Hz)'); ylabel('Amp dB');
endfunction


% limit mean of each vector to between lower_lim and upper_lim
function vout = limit_vec(vin, lower_lim, upper_lim)
  m = mean(vin');
  vout = zeros(size(vin));
  for i=1:length(vin)
    vec_no_mean = vin(i,:) - m(i);
    if m(i) < lower_lim
      m(i) = lower_lim;
    end
    if m(i) > upper_lim
      m(i) = upper_lim;
    end
    vout(i,:) = vec_no_mean + m(i);
  end
endfunction


% single stage vq a target matrix
function errors = vq_targets(vq, targets)
  errors = [];
  for i=1:length(targets)
    [mse_list index_list] = search_vq(vq, targets(i,:), 1);
    error = targets(i,:) - vq(index_list(1),:);
    errors = [errors; error];
  end
endfunction


% single stage vq a target matrix with adaptive EQ, this didn't work

function [errors eqs] = vq_targets_adap_eq(vq, targets, eqs)
  errors = []; gain=0.02;
  eq = eqs(end,:);
  for i=1:length(targets)
    t = targets(i,:) - eq;
    mean(t)
    %t -= mean(t);
    [mse_list index_list] = search_vq(vq, t, 1);
    error = t - vq(index_list(1),:);
    eq = (1-gain)*eq + gain*error;
    errors = [errors; error]; eqs = [eqs; eq];
  end
endfunction


% single stage vq a target matrix with block adaptive EQ, this works
% well with nblock == 10

function [errors eq] = vq_targets_block_eq(vq, targets, eq, nblock)
  errors = []; n = 0; [tmp K] = size(vq); error_eq = zeros(1,K); gain=0.20;
  for i=1:length(targets)
    t = targets(i,:) - eq;
    [mse_list index_list] = search_vq(vq, t, 1);
    error = t - vq(index_list(1),:);
    error_eq += error;
    errors = [errors; error];
    n++;
    if n == nblock
      eq = 0.99*eq + gain*error_eq/nblock;
      n = 0; error_eq = zeros(1,K);
    end  
  end
endfunction


% two stage mbest VQ a target matrix

function [errors targets_] = vq_targets2(vq1, vq2, targets)
  vqset(:,:,1)= vq1; vqset(:,:,2)=vq2; m=5;
  [errors targets_] = mbest(vqset, targets, m);
endfunction


% two stage mbest VQ a target matrix, with adap_eq

function [errors targets_ eq] = vq_targets2_adap_eq(vq1, vq2, targets, eq)
  vqset(:,:,1)= vq1; vqset(:,:,2)=vq2; m=5; gain=0.02;
  errors = []; targets_ = [];
  for i=1:length(targets)
    t = targets(i,:)-eq;
    t -= mean(t')';
    [error target_ indexes] = mbest(vqset, t, m);
    % use first stage VQ as error driving adaptive EQ
    eq_error = t - vq1(indexes(1),:);
    eq = (1-gain)*eq + gain*eq_error;
    errors = [errors; error]; targets_ = [targets_; target_];
  end
endfunction


% Given target and vq matrices, estimate eq via two metrics.  First
% metric seems to work best.  Both uses first stage VQ error for EQ

function [eq1 eq2] = est_eq(vq, targets)
  [ntargets K] = size(targets);
  [nvq K] = size(vq);
  
  eq1 = zeros(1,K);  eq2 = zeros(1,K);
  for i=1:length(targets)
    [mse_list index_list] = search_vq(vq, targets(i,:), 1);

    % eq metric 1: average of error for best VQ entry
    eq1 += targets(i,:) - vq(index_list(1),:);
    
    % eq metric 2: average of error across all VQ entries
    for j=1:nvq
      eq2 += targets(i,:) - vq(j,:);
    end
  end

  eq1 /= ntargets;
  eq2 /= (ntargets*nvq);
endfunction

function [targets e] = load_targets(fn_target_f32)
  nb_features = 41;
  K = 20;

  % .f32 files are in scripts directory, first K values rate_K_no_mean vectors
  [dir name ext] = fileparts(fn_target_f32);
  fn = sprintf("../script/%s_feat.f32", name);
  feat = load_f32(fn, nb_features);
  e = feat(:,1);
  targets = feat(:,2:K+1);
endfunction

% rather simple EQ in front of VQ

function [eqs ideal] = est_eq_front(targets)
  [tmp K] = size(targets);
  ideal = [ 8 10 12 14 14*ones(1,K-1-4) -20];
  eq = zeros(1,K); gain = 0.02;
  eqs = [];
  for i=1:length(targets)
    update = targets(i,:) - ideal;
    eq = (1-gain)*eq + gain*update;
    eq(find(eq < 0)) = 0;
    eqs = [eqs; eq];
  end
endfunction

function table_across_samples
  K = 20;

  % VQ is in .txt file in this directory, we have two to choose from.  train_120 is the Codec 2 700C VQ,
  % train_all_speech was trained up from a different, longer database, as a later exercise
  vq_name = "train_120";
  #vq_name = "train_all_speech";  
  vq1 = load(sprintf("%s_1.txt", vq_name));
  vq2 = load(sprintf("%s_2.txt", vq_name));
  
  printf("----------------------------------------------------------------------------------\n");
  printf("Sample                Initial  vq1     vq1_eq2  vq1_eq2  vq2    vq2_eq1  vq2_eq2 \n");
  printf("----------------------------------------------------------------------------------\n");
            
  fn_targets = { "cq_freedv_8k_lfboost" "cq_freedv_8k_hfcut" "cq_freedv_8k" "hts1a" "hts2a" "cq_ref" "ve9qrp_10s" "vk5qi" "c01_01_8k" "ma01_01"};
  #fn_targets = {"cq_freedv_8k_lfboost"};
  figs=1;
  for i=1:length(fn_targets)

    % load target and estimate eq
    [targets e] = load_targets(fn_targets{i});
    eq1 = est_eq(vq1, targets);
    eq2s = est_eq_front(targets);
    % for these simulation uses fixed EQ sample, rather than letting it vary frame by frame
    eq2 = eq2s(end,:);
    
    % first stage VQ -----------------
    
    errors1 = vq_targets(vq1, targets);
    errors1_eq1 = vq_targets(vq1, targets-eq1);
    errors1_eq2 = vq_targets(vq1, targets-eq2);
    
    % two stage mbest VQ --------------
    
    [errors2 targets_] = vq_targets2(vq1, vq2, targets);
    [errors2_eq1 targets_eq1_] = vq_targets2(vq1, vq2, targets-eq1);
    [errors2_eq2 targets_eq2_] = vq_targets2(vq1, vq2, targets-eq2);

    % save to .f32 files for listening tests
    if strcmp(vq_name,"train_120")
      save_f32(sprintf("../script/%s_vq2.f32", fn_targets{i}), targets_);
      save_f32(sprintf("../script/%s_vq2_eq1.f32", fn_targets{i}), targets_eq1_);
      save_f32(sprintf("../script/%s_vq2_eq2.f32", fn_targets{i}), targets_eq2_);
    else
      save_f32(sprintf("../script/%s_vq2_as.f32", fn_targets{i}), targets_);
      save_f32(sprintf("../script/%s_vq2_as_eq.f32", fn_targets{i}), targets_eq_);
    end 
    printf("%-21s %6.2f  %6.2f  %6.2f   %6.2f   %6.2f  %6.2f  %6.2f\n", fn_targets{i},
            var(targets(:)), var(errors1(:)), var(errors1_eq1(:)), var(errors1_eq2(:)),
            var(errors2(:)), var(errors2_eq1(:)), var(errors2_eq2(:)));

    figure(figs++); clf;
    %plot(var(errors2'),'b;vq2;'); hold on; plot(var(errors2_eq1'),'g;vq2_eq1;'); plot(var(errors2_eq2'),'r;vq2_eq2;'); hold off;
    plot(mean(targets),'g;mean(targets);'); hold on; plot(mean(vq1),'g;mean(vq1);'); plot(eq2,'r;eq2;'); hold off;
    title(fn_targets{i}); axis([1 K -20 30]);
   end
endfunction


% interactve, menu driven frame by frame plots

function interactive(fn_vq_txt, fn_target_f32)
  K = 20;
  vq = load("train_120_1.txt");
  [targets e] = load_targets(fn_target_f32);
  eq1 = est_eq(vq, targets);

  [errors1_eq2 eqs2] = vq_targets_adap_eq(vq, targets, zeros(1,K));
  [errors1_eq2 eqs2] = vq_targets_adap_eq(vq, targets, eqs2(end,:));
  eq2 = eqs2(end,:);
  
  figure(1); clf;
  mesh(e+targets)
  figure(2); clf;
  plot(eq1,'b;eq1;')
  hold on;
  plot(mean(targets),'c;mean(targets);'); plot(eq2,'g;eq2;');
  hold off;
  figure(3); clf; mesh(eqs2); title('eq2 evolving')

  % enter single step loop
  f = 20; neq = 0; eq=zeros(1,K);
  do 
    figure(4); clf;
    t = targets(f,:) - eq;
    [mse_list index_list] = search_vq(vq, t, 1);
    error = t - vq(index_list(1),:);
    plot(e(f)+t,'b;target;');
    hold on;
    plot(e(f)+vq(index_list,:),'g;vq;');
    plot(error,'r;error;');
    plot(eq,'c;eq;');
    plot([1 K],[e(f) e(f)],'--')
    hold off;
    axis([1 K -20 80])
    % interactive menu 

    printf("\r f: %2d eq: %d ind: %3d var: %3.1f menu: n-next  b-back  e-eq q-quit", f, neq, index_list(1), var(error));
    fflush(stdout);
    k = kbhit();

    if k == 'n' f+=1; end
    if k == 'e'
      neq++;
    end
    if neq == 3 neq = 0; end
    if neq == 0 eq = zeros(1,K); end
    if neq == 1 eq = eq1; end
    if neq == 2 eq = eqs2(f,:); end
    if k == 'b' f-=1; end
  until (k == 'q')
  printf("\n");
endfunction


% Experiment to test iterative approach of block update and remove
% mean (ie frame energy), shows some promise at reducing HF energy
% over several iterations while not affecting already good samples

function experiment_iterate_block(fn_vq_txt, fn_target_f32)
  K = 20;
  vq = load("train_120_1.txt");
  [targets e] = load_targets(fn_target_f32);

  figure(1); clf;
  plot(mean(targets),'b;mean(targets);');
  hold on;
  plot(mean(vq), 'g;mean(vq);');
  figure(2); clf; hold on;
  eq = zeros(1,K);
  for i=1:3
    [errors eq] = vq_targets_block_eq(vq, targets, eq, 10);    
    figure(1); plot(mean(targets-eq));
    figure(2); plot(eq);
    printf("i: %d %6.2f\n", i, var(errors(:)))
  end
endfunction

% Experiment to test EQ of input (before) VQ.  We set a threshold on
% when to equalise, so we don't upset already flat-ish samples.  This
% is the algorithm used for C at the time of writing (newamp1.c, newamp_700c.m)

function experiment_front_eq(fn_vq_txt, fn_target_f32)
  K = 20;
  vq = load("train_120_1.txt");
  [targets e] = load_targets(fn_target_f32);

  [eqs ideal] = est_eq_front(targets);
  
  figure(1); clf;
  plot(mean(targets),'b;mean(targets);');
  hold on;
  plot(ideal, 'g;ideal;');
  plot(eqs(end,:), 'r;eq;');
  plot(mean(targets)-eqs(end,:), 'c;equalised;');
  plot(mean(vq),'b--;mean(vq);');
  hold off;
  figure(2); clf; mesh(eqs(1:100,:)); title('EQ weights over time');
  ylabel('Time (frames'); xlabel('Freq (mel)');
endfunction

more off

% choose one of these to run first
% You'll need to run scripts/train_700C_quant.sh first to generate the .f32 files

%interactive("train_120_1.txt", "cq_freedv_8k_lfboost.f32")
%table_across_samples;
%vq_700c_plots({"all_speech_8k.f32" "all_speech_8k_hp300.f32" "dev-clean-8k.f32" "train_8k.f32" } )
%vq_700c_plots({"ve9qrp_10s.f32" "cq_freedv_8k_lfboost.f32" "cq_ref.f32" "hts1a.f32" "vk5qi.f32"})
%experiment_iterate_block("train_120_1.txt", "ve9qrp_10s.f32")
%experiment_iterate_block("train_120_1.txt", "cq_freedv_8k_lfboost.f32")
%experiment_front_eq("train_120_1.txt", "cq_freedv_8k_lfboost.f32")
