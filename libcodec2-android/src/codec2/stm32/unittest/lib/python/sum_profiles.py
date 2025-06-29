#!/usr/bin/env python3
""" sum_profiles """

def sum_profiles(fin, frames):
    data = {}
    total_time = 0.0
    active = False

    for line in fin:
        if   (not active): active = line.startswith("Start Profile Data")
        elif line.startswith("End Profile Data"): active = False
        else:
            words = line.strip().split()
            if (len(words) == 3):
                part = words[0]
                time_str = words[1]
                time = float(time_str)
                total_time += time
                if (not part in data): data[part] = 0.0
                data[part] += time
            # end else 
        # end for line

    data_sorted = [(p, data[p]) for p in sorted(data, key=data.get, reverse=True)]

    print("Total time = {:.1f} ms".format(total_time))
    if (frames):
        print("{:.1f} ms per frame".format(total_time / frames))
        print("")

    for part, time in data_sorted:
        percent = int(100*(time / total_time))
        print('{:2d}% - {:10.3f} - {}'.format(percent, time, part))

    return(data)
    # end sum_profiles()


########################################
if __name__ == "__main__":
    import argparse

    #### Options 
    argparser = argparse.ArgumentParser()
    argparser.add_argument("-f", "--frames", action="store", type=int, default=0,
                                             help="Number of frames")
    argparser.add_argument("file", metavar="FILE", help="file to read")
    args = argparser.parse_args()

    fin = open(args.file, "r")
    sum_profiles(fin, args.frames)
