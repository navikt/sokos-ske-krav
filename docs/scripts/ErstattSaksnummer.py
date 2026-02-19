import sys
import random

def main():
    if len(sys.argv) != 2:
        print("Usage: python scriptname.py filename")
        sys.exit(1)

    filename = sys.argv[1]

    with open(filename, 'r') as f:
        lines = f.readlines()

    if len(lines) < 3:
        # Not enough lines to process
        print("File has less than 3 lines")
        sys.exit(1)

    new_lines = []
    new_lines.append(lines[0])  # Keep the first line unchanged

    for line in lines[1:-1]:
        line = line.rstrip('\n')  # Remove the newline character for accurate indexing
        line = line.rstrip('\n').replace('_', '-')
        if len(line) >= 19:
            rand_num = random.randint(1111111, 9999999)
            rand_str = str(rand_num)
            # Replace characters from index 11 to 18 (positions 12 to 19 inclusive)
            new_line = line[:11] + rand_str + line[18:]
            new_lines.append(new_line + '\n')  # Add the newline character back
        else:
            # If the line is too short, leave it unchanged
            new_lines.append(line + '\n')

    new_lines.append(lines[-1])  # Keep the last line unchanged

    # Write the modified lines back to the file
    with open(filename, 'w') as f:
        f.writelines(new_lines)

if __name__ == '__main__':
    main()

