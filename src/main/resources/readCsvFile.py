import csv

data_file_path = '/tmp/earth-ship-events.csv'

with open(data_file_path, 'r') as file:
    reader = csv.reader(file)

    for row in reader:
        print(row)
        time_in_ms, event_from_type, event_from_id, event_to_type, event_to_id = [cell.strip() for cell in row]
