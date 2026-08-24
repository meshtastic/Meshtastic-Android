import xml.etree.ElementTree as ET
import os
import re

# Meshtastic String Sorter & Indexer
# Usage: python3 scripts/sort-strings.py
# This script alphabetizes strings.xml, adds prefix markers, and regenerates strings-index.txt.

def sort_strings(xml_path, index_path):
    print(f"Reading {xml_path}...")
    with open(xml_path, 'r', encoding='utf-8', newline='\n') as f:
        content = f.read()

    # Extract license header
    header_match = re.search(r'^(.*?)<resources>', content, re.DOTALL)
    header = header_match.group(1) if header_match else '<?xml version="1.0" encoding="utf-8"?>\n'

    # Parse XML
    tree = ET.parse(xml_path)
    root = tree.getroot()

    # Extract elements and their names
    elements = []
    for child in root:
        name = child.get('name')
        if name:
            elements.append((name, child))

    # Sort elements by name
    elements.sort(key=lambda x: x[0])

    # Group by prefix
    grouped_elements = {}
    for name, element in elements:
        prefix = name.split('_')[0]
        if prefix not in grouped_elements:
            grouped_elements[prefix] = []
        grouped_elements[prefix].append(element)

    # Reconstruct XML and prepare Index
    new_root = ET.Element('resources')
    index_lines = []

    sorted_prefixes = sorted(grouped_elements.keys())

    for prefix in sorted_prefixes:
        group = grouped_elements[prefix]
        if len(group) >= 5:
            marker = prefix.upper()
            new_root.append(ET.Comment(f' {marker} '))
            index_lines.append(f"### {marker} ###")

        for element in group:
            new_root.append(element)
            index_lines.append(element.get('name'))

    # Pretty print helper
    def prettify(elem, level=0):
        indent = "    "
        if len(elem):
            if not elem.text or not elem.text.strip():
                elem.text = "\n" + (level + 1) * indent
            if not elem.tail or not elem.tail.strip():
                elem.tail = "\n" + level * indent
            for i, child in enumerate(elem):
                prettify(child, level + 1)
                if i < len(elem) - 1:
                    if not child.tail or not child.tail.strip():
                        child.tail = "\n" + (level + 1) * indent
                else:
                    if not child.tail or not child.tail.strip():
                        child.tail = "\n" + level * indent
        else:
            if level and (not elem.tail or not elem.tail.strip()):
                elem.tail = "\n" + level * indent

    prettify(new_root)

    # Write XML
    xml_str = ET.tostring(new_root, encoding='unicode')
    final_content = header + xml_str + '\n'
    with open(xml_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(final_content)
    print(f"Successfully sorted {xml_path}")

    # Write Index
    with open(index_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(index_lines) + '\n')
    print(f"Successfully regenerated {index_path}")

if __name__ == "__main__":
    xml_file = 'core/resources/src/commonMain/composeResources/values/strings.xml'
    index_file = '.skills/compose-ui/strings-index.txt'

    if os.path.exists(xml_file):
        sort_strings(xml_file, index_file)
    else:
        print(f"Error: {xml_file} not found.")
