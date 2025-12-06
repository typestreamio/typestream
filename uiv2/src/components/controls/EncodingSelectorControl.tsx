import { Encoding } from '../../generated/job_pb';

export function EncodingSelectorControl({ value, onChange }: {
  value: Encoding;
  onChange: (value: Encoding) => void;
}) {
  return (
    <select value={value} onChange={(e) => onChange(Number(e.target.value) as Encoding)}>
      <option value={Encoding.STRING}>STRING</option>
      <option value={Encoding.NUMBER}>NUMBER</option>
      <option value={Encoding.JSON}>JSON</option>
      <option value={Encoding.AVRO}>AVRO</option>
      <option value={Encoding.PROTOBUF}>PROTOBUF</option>
    </select>
  );
}
