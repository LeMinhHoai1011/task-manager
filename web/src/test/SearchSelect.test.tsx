import {fireEvent,render,screen} from "@testing-library/react";
import {expect,test,vi} from "vitest";
import SearchSelect from "../components/SearchSelect";

test("tìm không dấu, phân biệt tên trùng và chọn bằng bàn phím",()=>{
  const changed=vi.fn();
  render(<SearchSelect label="Người phụ trách" value="" onChange={changed} options={[{id:"one",name:"Đặng An",detail:"@an.mot"},{id:"two",name:"Đặng An",detail:"@an.hai"}]}/>);
  fireEvent.click(screen.getByRole("button",{name:"Người phụ trách"}));
  const input=screen.getByRole("combobox");
  fireEvent.change(input,{target:{value:"dang an"}});
  expect(screen.getAllByRole("option")).toHaveLength(2);
  fireEvent.keyDown(input,{key:"ArrowDown"});fireEvent.keyDown(input,{key:"Enter"});
  expect(changed).toHaveBeenCalledWith("two");
  expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  expect(screen.getByRole("button",{name:"Người phụ trách"})).toHaveFocus();
});

test("báo lỗi tải, thử lại, tải thêm và giữ lựa chọn khi đóng",()=>{
  const retry=vi.fn(),more=vi.fn(),changed=vi.fn();
  render(<SearchSelect label="Dự án" value="one" options={[{id:"one",name:"Dự án A"}]} onChange={changed} error="Lỗi tải danh sách" onRetry={retry} onMore={more}/>);
  fireEvent.click(screen.getByRole("button",{name:"Dự án"}));
  expect(screen.getByRole("alert")).toHaveTextContent("Lỗi tải danh sách");
  fireEvent.click(screen.getByRole("button",{name:"Thử lại"}));expect(retry).toHaveBeenCalledOnce();
  fireEvent.click(screen.getByRole("button",{name:"Tải thêm lựa chọn"}));expect(more).toHaveBeenCalledOnce();
  fireEvent.keyDown(screen.getByRole("combobox"),{key:"Escape"});
  expect(changed).not.toHaveBeenCalled();
});
