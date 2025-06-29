% melvq.m
% David Rowe Aug 2015
%
% Experimenting with VQ design for mel LSPs, also handy VQ searching routines

1;

% train up multi-stage VQ
% ~/codec2-dev/build_linux/src$ sox -r 8000 -s -2 ../../wav/all.wav -t raw -r 8000 -s -2 - sinc 300 sinc -2600 | ./c2sim - --lpc 6 --lpcpf --lspmel --dump all  -o - | play -t raw -r 8000 -s -2 - vol 3
%
% octave:> load ../build_linux/src/all_mel.txt
% octave:> melvq; vq = trainvq(all_mel, 64, 3);
% octave:> save vq

function vq = trainvq(training_data, Nvec, stages, city_en=0)

  vq = [];
  for i=1:stages
    if city_en
      [idx centers] = kmeans(training_data, Nvec, 'DISTANCE', 'cityblock');
    else
      [idx centers] = kmeans(training_data, Nvec);
    end
    quant_error = centers(idx,:) - training_data;
    printf("mse stage %d: %f\n", i, mean(std(quant_error)));
    training_data = quant_error;
    vq(:,:,i) = centers;
  end

end

function [mse_list index_list] = search_vq(vq, target, m)

  [Nvec order] = size(vq);

  mse = zeros(1, Nvec);
 
  % find mse for each vector

  for i=1:Nvec
     mse(i) = sum((target - vq(i,:)) .^2);
  end

  % sort and keep top m matches

  [mse_list index_list ] = sort(mse);

  mse_list = mse_list(1:m);
  index_list = index_list(1:m);

endfunction


% Search multi-stage VQ, retaining m best candidates at each stage

function [res output_vecs ind] = mbest(vqset, input_vecs, m)

  [Nvec order stages] = size(vqset);
  [Ninput tmp] = size(input_vecs);

  res = [];         % residual error after VQ
  output_vecs = []; % quantised ouput vectors
  ind = [];         % index of vqs
  
  for i=1:Ninput
  
    % first stage, find mbest candidates

    [mse_list index_list] = search_vq(vqset(:,:,1), input_vecs(i,:), m);
    cand_list = [mse_list' index_list'];
    cand_list = sortrows(cand_list,1);

    % subsequent stages ...........

    for s=2:stages

      % compute m targets for next stage, and update path

      prev_indexes = zeros(m,s-1);
      for t=1:m
        target(t,:) = input_vecs(i,:);
        for v=1:s-1
          target(t,:) -= vqset(cand_list(t,v+1),:,v);
        end
        prev_indexes(t,:) = cand_list(t,2:s);
      end
      
      % search stage s using m targets from stage s-1
      % with m targets, we do m searches which return the m best possibilities
      % so we get a matrix with one row per candidate, m*m rows total
      % prev_indexes provides us with the path through the VQs for each candidate row

      avq = vqset(:,:,s);
      cand_list = [];
      for t=1:m
        [mse_list index_list] = search_vq(avq, target(t,:), m);
        x = ones(m,1)*prev_indexes(t,:);
        cand_row = [mse_list' x index_list'];
        cand_list = [cand_list; cand_row];
      end

      % sort into m best rows
     
      cand_list = sortrows(cand_list,1);
      cand_list = cand_list(1:m,:);

    end

    % final residual
    target(1,:) = input_vecs(i,:);
    out = zeros(1,order);
    for v=1:stages
      target(1,:) -= vqset(cand_list(1,v+1),:,v);
      out += vqset(cand_list(1,v+1),:,v);
    end
    res  = [res; target(1,:)];
    output_vecs  = [output_vecs; out];
    ind  = [ind; cand_list(1,2:1+stages)];
  end

endfunction


% Quantises a set of mel-lsps and saves back to disk so they can be read in by c2sim
% assumes we have a vq saved to disk called vq
%
% ~/codec2-dev/build_linux/src$ sox -r 8000 -s -2 ../../wav/vk5qi.wav -t raw -r 8000 -s -2 - sinc 300 sinc -2600 | ./c2sim - --lpc 6 --lpcpf --lspmel --dump vk5qi  -o - | play -t raw -r 8000 -s -2 - vol 3
%
% octave:> test_run("vk5qi")
%
% ~/codec2-dev/build_linux/src$ sox -r 8000 -s -2 ../../wav/vk5qi.wav -t raw -r 8000 -s -2 - sinc 300 sinc -2600 | ./c2sim - --lpc 6 --lpcpf --phase0 --dec 4 --postfilter --lspmel --lspmelread ../../octave/vk5qi_mel_.out -o - | play -t raw -r 8000 -s -2 - vol 3

function ind = test_run(samplename)

  more off;
  input_vecs_name = sprintf("../build_linux/src/%s_mel.txt", samplename);
  input_vecs_name
  mel = load(input_vecs_name);
  load vq;
  [res mel_ ind] = mbest(vq, mel, 5);
  mean(std(res))

  output_vecs_name = sprintf("%s_mel_.out", samplename);
  fmel_ = fopen(output_vecs_name,"wb"); 
  [r c] = size(mel_);
  for i=1:r
    fwrite(fmel_, mel_(i,:), "float32"); 
  end
  fclose(fmel_);
end

%ind = test_run("hts1a");

%load "../build_linux/src/all_mel.txt"
%vq = trainvq(all_mel, 64, 3);
%save vq;

% [X] save text file of "vq quantised mels"
% [X] load back into c2sim at run time
% [X] train on continuous mels
% [X] sorting/stability
% [X] see how it sounds
% [X] Goal is to get VQ sounding OK, similar to UQ at 20 or 40ms dec,
% [X] sig better than current 700
% [X] check all indexes used with hist
