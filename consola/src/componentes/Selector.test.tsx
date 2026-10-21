import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { filtrarOpciones, Selector, type OpcionSelector } from './Selector'

const CASOS: OpcionSelector[] = [
  'Operación normal',
  'Cambio de tasa',
  'Dación en pago',
  'Retoma con excedente',
  'Pago anticipado total',
  'Abono extra como menor plazo',
  'Abono extra como menor cuota',
  'Pago inferior',
  'Pago tardío y gastos de cobranza',
].map((t, i) => ({ valor: String(i + 1), titulo: `Caso ${i + 1} · ${t}`, descripcion: i === 3 ? 'Se recoge la moto' : undefined }))

const POCAS: OpcionSelector[] = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'].map((d) => ({ valor: d, titulo: d }))

function Prueba({ opciones, alCambiar }: Readonly<{ opciones: OpcionSelector[]; alCambiar: (v: string) => void }>) {
  const [valor, setValor] = useState(opciones[0].valor)
  return (
    <Selector
      etiqueta="Elige"
      valor={valor}
      opciones={opciones}
      alCambiar={(v) => {
        setValor(v)
        alCambiar(v)
      }}
    />
  )
}

afterEach(cleanup)

describe('Selector', () => {
  it('filtra sin tildes ni mayúsculas, por título y descripción', () => {
    expect(filtrarOpciones(CASOS, 'dacion').map((o) => o.valor)).toEqual(['3'])
    expect(filtrarOpciones(CASOS, 'MOTO').map((o) => o.valor)).toEqual(['4'])
    expect(filtrarOpciones(CASOS, 'abono cuota').map((o) => o.valor)).toEqual(['7'])
    expect(filtrarOpciones(CASOS, '  ')).toHaveLength(CASOS.length)
  })

  it('con más de 7 opciones abre con buscador; flechas, Enter y Esc', () => {
    const alCambiar = vi.fn()
    render(<Prueba opciones={CASOS} alCambiar={alCambiar} />)
    const boton = screen.getByRole('button', { name: /Elige/ })
    expect(boton.getAttribute('aria-haspopup')).toBe('listbox')
    expect(boton.getAttribute('aria-expanded')).toBe('false')
    fireEvent.keyDown(boton, { key: 'ArrowDown' })
    expect(boton.getAttribute('aria-expanded')).toBe('true')
    const buscar = screen.getByRole('combobox', { name: /Buscar/ })
    expect(document.activeElement).toBe(buscar)
    expect(screen.getAllByRole('option')).toHaveLength(9)
    expect(screen.getByRole('option', { selected: true }).textContent).toContain('Caso 1')
    fireEvent.change(buscar, { target: { value: 'abono' } })
    expect(screen.getAllByRole('option')).toHaveLength(2)
    fireEvent.keyDown(buscar, { key: 'ArrowDown' })
    expect(buscar.getAttribute('aria-activedescendant')).toBe(screen.getAllByRole('option')[1].id)
    fireEvent.keyDown(buscar, { key: 'Enter' })
    expect(alCambiar).toHaveBeenCalledWith('7')
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(document.activeElement).toBe(boton)
    expect(boton.textContent).toContain('Caso 7')
    // Esc cierra sin cambiar
    fireEvent.click(boton)
    fireEvent.keyDown(screen.getByRole('combobox', { name: /Buscar/ }), { key: 'Escape' })
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(alCambiar).toHaveBeenCalledTimes(1)
  })

  it('escribir sobre el botón abre el buscador con esa letra', () => {
    render(<Prueba opciones={CASOS} alCambiar={() => undefined} />)
    fireEvent.keyDown(screen.getByRole('button', { name: /Elige/ }), { key: 'r' })
    expect((screen.getByRole('combobox', { name: /Buscar/ }) as HTMLInputElement).value).toBe('r')
  })

  it('con pocas opciones no hay buscador: la lista toma el foco y escribir salta a la opción', () => {
    const alCambiar = vi.fn()
    render(<Prueba opciones={POCAS} alCambiar={alCambiar} />)
    fireEvent.click(screen.getByRole('button', { name: /Elige/ }))
    expect(screen.queryByRole('combobox')).toBeNull()
    const lista = screen.getByRole('listbox')
    expect(document.activeElement).toBe(lista)
    fireEvent.keyDown(lista, { key: 'v' })
    expect(lista.getAttribute('aria-activedescendant')).toBe(screen.getByRole('option', { name: /Viernes/ }).id)
    fireEvent.keyDown(lista, { key: 'Home' })
    fireEvent.keyDown(lista, { key: 'ArrowDown' })
    fireEvent.keyDown(lista, { key: 'Enter' })
    expect(alCambiar).toHaveBeenCalledWith('Martes')
  })

  it('un clic en una opción la elige y un clic afuera cierra', () => {
    const alCambiar = vi.fn()
    render(<Prueba opciones={POCAS} alCambiar={alCambiar} />)
    fireEvent.click(screen.getByRole('button', { name: /Elige/ }))
    fireEvent.click(screen.getByRole('option', { name: /Jueves/ }))
    expect(alCambiar).toHaveBeenCalledWith('Jueves')
    fireEvent.click(screen.getByRole('button', { name: /Elige/ }))
    fireEvent.pointerDown(document.body)
    expect(screen.queryByRole('listbox')).toBeNull()
  })
})
