import sys

def main():
    if len(sys.argv) != 3:
        print("Usage: python scriptname.py source_file target_file")
        print("  source_file: File to read saksnummer from")
        print("  target_file: File to insert saksnummer into")
        sys.exit(1)

    source_filename = sys.argv[1]
    target_filename = sys.argv[2]

    # Read saksnummer from source file
    with open(source_filename, 'r') as f:
        source_lines = f.readlines()

    if len(source_lines) < 2:
        print("Source file has less than 2 lines")
        sys.exit(1)

    # Read target file
    with open(target_filename, 'r') as f:
        target_lines = f.readlines()

    if len(target_lines) < 2:
        print("Target file has less than 2 lines")
        sys.exit(1)

    # Build a dictionary of saksnummer from source file
    # Key: Exxx suffix, Value: full saksnummer (18 chars)
    saksnummer_map = {}

    for i, line in enumerate(source_lines[1:-1], start=1):
        source_line = line.rstrip('\n')
        if len(source_line) >= 29:
            # Extract full saksnummer (18 chars) from positions 11-29 (indices 11:29)
            saksnummer_full = source_line[11:29]
            # Extract the Exxx suffix (last 4 characters)
            exxx_suffix = saksnummer_full[-4:]
            saksnummer_map[exxx_suffix] = saksnummer_full
        else:
            print(f"Warning: Source line {i+1} is too short to extract saksnummer")

    print(f"Extracted {len(saksnummer_map)} saksnummer from source file")

    # Process target file - find gammelref with matching Exxx and replace with saksnummer
    new_lines = []
    new_lines.append(target_lines[0])  # Keep the first line unchanged

    matched_count = 0

    for i, line in enumerate(target_lines[1:-1], start=1):
        target_line = line.rstrip('\n')

        if len(target_line) >= 101:
            # Extract gammelref (18 chars) from positions 83-100 (indices 83:101)
            gammelref = target_line[83:101]

            # Check if any Exxx suffix matches this gammelref
            match_found = False
            for exxx_suffix, saksnummer_full in saksnummer_map.items():
                if exxx_suffix in gammelref:
                    # Replace gammelref with the saksnummer
                    new_line = target_line[:83] + saksnummer_full + target_line[101:]
                    new_lines.append(new_line + '\n')
                    matched_count += 1
                    match_found = True
                    break

            if not match_found:
                # No match found, keep line unchanged
                new_lines.append(target_line + '\n')
        else:
            # Line too short, keep unchanged
            new_lines.append(target_line + '\n')

    new_lines.append(target_lines[-1])  # Keep the last line unchanged

    print(f"Matched and replaced {matched_count} gammelref entries")

    # Write the modified lines back to the target file
    with open(target_filename, 'w') as f:
        f.writelines(new_lines)

    print(f"Saksnummer inserted into {target_filename}")

if __name__ == '__main__':
    main()

